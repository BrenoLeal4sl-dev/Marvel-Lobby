package com.example.marvellobby.data.repository

import com.example.marvellobby.data.local.*
import com.example.marvellobby.data.remote.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID
import java.util.Locale

class OnlineAccountRepository(private val dao: ArchiveDao,private val api: LobbyTransport,
    private val tokens: OnlineTokens,private val avatars: AvatarRepository) {
    val available get()=api.origin.isNotBlank()
    private val mutex=Mutex()
    private val validity=MutableStateFlow<Map<String,Boolean>>(emptyMap())
    val sessionValidity=validity.asStateFlow()
    private val gson=Gson()
    private fun avatar(id: Int?)=avatars.choices.firstOrNull { it.id==id }?.uri.orEmpty()
    private fun avatarId(uri: String)=avatars.choices.firstOrNull { it.uri==uri }?.id
    private fun row(profile: UserProfile)=RemoteAccount().apply { owner=profile.ownerKey;payload=gson.toJson(profile) }
    private fun expired()=LobbyApiException("SESSION_EXPIRED",401,"Sign in again to continue.")
    private fun invalidate(owner: String): Nothing {
        tokens.remove(owner)
        validity.update { it+(owner to false) }
        throw expired()
    }
    private suspend fun persist(json: JSONObject,expectedOwner: String?=null): UserProfile {
        val session=LobbyProtocol.session(json,api.origin,::avatar)
        require(expectedOwner==null || session.user.ownerKey==expectedOwner) { "Invalid online session." }
        currentCoroutineContext().ensureActive()
        tokens.save(session.user.ownerKey,session.secrets);dao.saveRemoteAccount(row(session.user))
        validity.update { it+(session.user.ownerKey to true) }
        return session.user
    }
    suspend fun register(name: String,email: String,password: String,username: String,bio: String="",avatar: String="")=withContext(Dispatchers.IO) {
        mutex.withLock {
            val body=JSONObject().put("name",name.trim()).put("email",email.trim()).put("password",password).put("username",com.example.marvellobby.data.model.Usernames.normalize(username))
                .put("bio",bio).put("avatarId",avatarId(avatar) ?: JSONObject.NULL)
            withContext(NonCancellable) { persist(api.request("POST","/v1/auth/register",body)) }
        }
    }
    suspend fun login(email: String,password: String)=withContext(Dispatchers.IO) {
        mutex.withLock { withContext(NonCancellable) {
            persist(api.request("POST","/v1/auth/login",JSONObject().put("email",email).put("password",password)))
        } }
    }
    private suspend fun refresh(owner: String,secrets: SessionSecrets): SessionSecrets=withContext(NonCancellable) {
        // Rotation consumes the old refresh token on the server. Finish saving its replacement
        // even if a search, socket reconnect or screen is cancelled during the request.
        // Network timeouts still apply; the caller remains cancellable outside this transaction.
        try {
            persist(api.request("POST","/v1/auth/refresh",JSONObject().put("refreshToken",secrets.refreshToken)),owner)
            tokens.read(owner) ?: invalidate(owner)
        } catch(error: LobbyApiException) { if(error.status==401)invalidate(owner);throw error }
    }
    private suspend fun authorized(owner: String,method: String,path: String,body: JSONObject?=null): JSONObject {
        var secrets=tokens.read(owner) ?: invalidate(owner)
        if(secrets.origin!=api.origin || owner!="remote:${secrets.userId}")invalidate(owner)
        if(secrets.accessExpiresAt<=System.currentTimeMillis()+30_000)secrets=refresh(owner,secrets)
        currentCoroutineContext().ensureActive()
        return try { api.request(method,path,body,secrets.accessToken) }
        catch(error: LobbyApiException) {
            if(error.status!=401)throw error
            secrets=refresh(owner,secrets)
            currentCoroutineContext().ensureActive()
            try { api.request(method,path,body,secrets.accessToken) }
            catch(retry: LobbyApiException) { if(retry.status==401)invalidate(owner);throw retry }
        }
    }
    suspend fun me(owner: String)=withContext(Dispatchers.IO) {
        mutex.withLock {
            val profile=LobbyProtocol.profile(authorized(owner,"GET","/v1/me"),::avatar)
            require(profile.ownerKey==owner);currentCoroutineContext().ensureActive()
            dao.saveRemoteAccount(row(profile));profile
        }
    }
    suspend fun update(owner: String,name: String,username: String,bio: String,avatar: String,
        email: String,currentPassword: String,newPassword: String): UserProfile=withContext(Dispatchers.IO) {
        mutex.withLock {
            val previous=dao.remoteAccount(owner)?.let { gson.fromJson(it.payload,UserProfile::class.java) } ?: throw expired()
            val data=JSONObject().put("name",name).put("username",username).put("bio",bio).put("avatarId",avatarId(avatar) ?: JSONObject.NULL)
            if(email.trim().lowercase(Locale.ROOT)!=previous.email || newPassword.isNotEmpty()) {
                val body=JSONObject().put("email",email).put("currentPassword",currentPassword).put("newPassword",newPassword).put("profile",data)
                withContext(NonCancellable) { persist(authorized(owner,"PATCH","/v1/me/credentials",body),owner) }
            } else {
                val profile=LobbyProtocol.profile(authorized(owner,"PATCH","/v1/me",data),::avatar)
                require(profile.ownerKey==owner);currentCoroutineContext().ensureActive();dao.saveRemoteAccount(row(profile));profile
            }
        }
    }
    suspend fun publicProfile(owner: String,id: String)=withContext(Dispatchers.IO) {
        require(UUID.fromString(id).toString()==id)
        mutex.withLock { LobbyProtocol.profile(authorized(owner,"GET","/v1/users/$id"),::avatar) }
    }
    suspend fun bio(owner: String,value: String)=withContext(Dispatchers.IO) {
        val text=value.trim();require(text.codePointCount(0,text.length)<=280) { "Keep your bio within 280 characters." }
        mutex.withLock {
            val profile=LobbyProtocol.profile(authorized(owner,"PATCH","/v1/me/bio",JSONObject().put("bio",text)),::avatar)
            require(profile.ownerKey==owner);currentCoroutineContext().ensureActive()
            dao.saveRemoteAccount(row(profile));profile
        }
    }
    suspend fun communityRequest(owner: String,method: String,path: String,body: JSONObject?=null)=withContext(Dispatchers.IO) {
        require(path.startsWith("/v1/community/"))
        mutex.withLock { authorized(owner,method,path,body) }
    }
    suspend fun archiveRequest(owner: String,method: String,path: String,body: JSONObject?=null)=withContext(Dispatchers.IO) {
        require(path=="/v1/archive" || path=="/v1/archive/settings" || path.matches(Regex("/v1/archive\\?after=[0-9]+")))
        mutex.withLock { authorized(owner,method,path,body) }
    }
    suspend fun riftRequest(owner: String,method: String,path: String,body: JSONObject?=null)=withContext(Dispatchers.IO) {
        require(path.startsWith("/v1/rift/") && !path.contains("..") && !path.contains('#'))
        mutex.withLock { authorized(owner,method,path,body) }
    }
    suspend fun realtimeCredentials(owner: String)=withContext(Dispatchers.IO) {
        mutex.withLock {
            // Also validates revocation before each reconnect; refresh stays serialized with HTTP operations.
            authorized(owner,"GET","/v1/me")
            (tokens.read(owner) ?: throw expired()).also {
                require(it.origin==api.origin && owner=="remote:${it.userId}")
            }
        }
    }
    suspend fun bindLocal(localOwner: String,profile: UserProfile)=withContext(Dispatchers.IO) {
        require(profile.online);dao.bindLocalAccount(localOwner,row(profile))
    }
    /** Retire the exact old session; delayed revocation cannot log out a later login. */
    suspend fun retire(owner: String): SessionSecrets?=withContext(Dispatchers.IO) {
        mutex.withLock { tokens.read(owner).also { tokens.remove(owner) } }
    }
    suspend fun revoke(secrets: SessionSecrets)=withContext(Dispatchers.IO) {
        if(secrets.origin!=api.origin)return@withContext
        suspend fun renewedAccess(): String {
            val retired=LobbyProtocol.session(api.request("POST","/v1/auth/refresh",JSONObject().put("refreshToken",secrets.refreshToken)),api.origin,::avatar)
            require(retired.secrets.userId==secrets.userId) { "Invalid online session." }
            // Revoke only this retired family. Never persist it over a subsequent login.
            return retired.secrets.accessToken
        }
        if(secrets.accessExpiresAt<=System.currentTimeMillis()+30_000) {
            api.request("POST","/v1/auth/logout",bearer=renewedAccess())
        } else try { api.request("POST","/v1/auth/logout",bearer=secrets.accessToken) }
        catch(error: LobbyApiException) {
            if(error.status!=401)throw error
            api.request("POST","/v1/auth/logout",bearer=renewedAccess())
        }
    }
}
