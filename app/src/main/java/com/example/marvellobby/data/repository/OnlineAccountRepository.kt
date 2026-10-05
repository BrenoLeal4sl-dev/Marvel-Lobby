package com.example.marvellobby.data.repository

import com.example.marvellobby.data.local.*
import com.example.marvellobby.data.remote.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID
import java.util.Locale

class OnlineAccountRepository(private val dao: ArchiveDao,private val api: LobbyTransport,
    private val tokens: OnlineTokens,private val avatars: AvatarRepository) {
    val available get()=api.origin.isNotBlank()
    private val mutex=Mutex()
    private val gson=Gson()
    private fun avatar(id: Int?)=avatars.choices.firstOrNull { it.id==id }?.uri.orEmpty()
    private fun avatarId(uri: String)=avatars.choices.firstOrNull { it.uri==uri }?.id
    private fun row(profile: UserProfile)=RemoteAccount().apply { owner=profile.ownerKey;payload=gson.toJson(profile) }
    private fun expired()=LobbyApiException("SESSION_EXPIRED",401,"Sign in again to continue.")
    private suspend fun persist(json: JSONObject,expectedOwner: String?=null): UserProfile {
        val session=LobbyProtocol.session(json,api.origin,::avatar)
        require(expectedOwner==null || session.user.ownerKey==expectedOwner) { "Invalid online session." }
        currentCoroutineContext().ensureActive()
        tokens.save(session.user.ownerKey,session.secrets);dao.saveRemoteAccount(row(session.user))
        return session.user
    }
    suspend fun register(name: String,email: String,password: String,username: String,bio: String="",avatar: String="")=withContext(Dispatchers.IO) {
        mutex.withLock {
            val body=JSONObject().put("name",name).put("email",email).put("password",password).put("username",username)
                .put("bio",bio).put("avatarId",avatarId(avatar) ?: JSONObject.NULL)
            persist(api.request("POST","/v1/auth/register",body))
        }
    }
    suspend fun login(email: String,password: String)=withContext(Dispatchers.IO) {
        mutex.withLock { persist(api.request("POST","/v1/auth/login",JSONObject().put("email",email).put("password",password))) }
    }
    private suspend fun refresh(owner: String,secrets: SessionSecrets): SessionSecrets {
        try {
            persist(api.request("POST","/v1/auth/refresh",JSONObject().put("refreshToken",secrets.refreshToken)),owner)
            return tokens.read(owner) ?: throw expired()
        } catch(error: LobbyApiException) { if(error.status==401)tokens.remove(owner);throw error }
    }
    private suspend fun authorized(owner: String,method: String,path: String,body: JSONObject?=null): JSONObject {
        var secrets=tokens.read(owner) ?: throw expired()
        if(secrets.origin!=api.origin || owner!="remote:${secrets.userId}")throw expired()
        if(secrets.accessExpiresAt<=System.currentTimeMillis()+30_000)secrets=refresh(owner,secrets)
        return try { api.request(method,path,body,secrets.accessToken) }
        catch(error: LobbyApiException) {
            if(error.status!=401)throw error
            secrets=refresh(owner,secrets);api.request(method,path,body,secrets.accessToken)
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
                persist(authorized(owner,"PATCH","/v1/me/credentials",body),owner)
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
