package com.example.marvellobby

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.marvellobby.data.local.*
import com.example.marvellobby.data.remote.*
import com.example.marvellobby.data.repository.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OnlineIdentityTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun remote(id: String=UUID.randomUUID().toString())=UserProfile("Online hero","hero@example.invalid","","online_hero",id,"Hello",1_700_000_000_000)
    private fun row(user: UserProfile)=RemoteAccount().apply { owner=user.ownerKey;payload=Gson().toJson(user) }
    private fun seed(dao: ArchiveDao,owner: String) {
        dao.insertAccount(LocalAccount().apply { email=owner;name="Local";username="local_hero";passwordHash="hash";salt="salt";avatar="" })
    }

    private inner class SessionFixture: AutoCloseable {
        val db=Room.inMemoryDatabaseBuilder(context,MarvelDatabase::class.java).build()
        val user=remote()
        var secret: SessionSecrets?=null
        var refreshes=0
        var protectedCalls=0
        var failRefresh: Exception?=null
        var failProtected: Exception?=null
        var rotationStarted: CompletableDeferred<Unit>?=null
        var releaseRotation: CompletableDeferred<Unit>?=null
        val store=object:OnlineTokens {
            override fun read(owner:String)=secret
            override fun save(owner:String,secrets:SessionSecrets) { secret=secrets }
            override fun remove(owner:String) { secret=null }
        }
        val profile=JSONObject().put("id",user.id).put("name",user.name).put("email",user.email).put("username",user.username)
            .put("bio",user.bio).put("avatarId",JSONObject.NULL).put("joinedAt","2026-10-01T12:00:00Z")
        fun session(access: String,refresh: String)=JSONObject().put("user",profile).put("accessToken",access.repeat(43))
            .put("refreshToken",refresh.repeat(43)).put("accessExpiresAt","2036-10-01T12:00:00Z")
        val transport=object:LobbyTransport {
            override val origin="https://example.invalid"
            override suspend fun request(method:String,path:String,body:JSONObject?,bearer:String?):JSONObject=withContext(Dispatchers.IO) {
                when(path) {
                    "/v1/auth/login" -> session("a","r")
                    "/v1/auth/refresh","/v1/me/credentials" -> {
                        if(path.endsWith("refresh")) {
                            refreshes++;failRefresh?.let { throw it }
                            assertEquals("r".repeat(43),body!!.getString("refreshToken"))
                        }
                        rotationStarted?.complete(Unit)
                        releaseRotation?.await() // Server consumed old token; replacement response is in flight.
                        session("b","s")
                    }
                    else -> {
                        protectedCalls++;failProtected?.let { throw it }
                        assertEquals(secret!!.accessToken,bearer)
                        profile
                    }
                }
            }
        }
        val repository=OnlineAccountRepository(db.archive(),transport,store,AvatarRepository(context))
        suspend fun login() { repository.login(user.email,"Password2026") }
        override fun close() { db.close() }
    }

    @Test fun cancelledRefreshStillPersistsRotatedTokensAndDoesNotRunCancelledQuery()=runBlocking<Unit> {
        SessionFixture().use { fixture ->
            fixture.login();fixture.secret=fixture.secret!!.copy(accessExpiresAt=0)
            fixture.rotationStarted=CompletableDeferred();fixture.releaseRotation=CompletableDeferred()
            val request=launch { fixture.repository.communityRequest(fixture.user.ownerKey,"GET","/v1/community/people") }
            withTimeout(5000) { fixture.rotationStarted!!.await() }
            request.cancel();fixture.releaseRotation!!.complete(Unit)
            withTimeout(5000) { request.join() }
            assertEquals("s".repeat(43),fixture.secret!!.refreshToken)
            assertEquals(0,fixture.protectedCalls)
            fixture.repository.communityRequest(fixture.user.ownerKey,"GET","/v1/community/people")
            assertEquals(1,fixture.refreshes);assertEquals(1,fixture.protectedCalls)
        }
    }

    @Test fun cancelledCredentialChangeStillSavesItsReplacementSession()=runBlocking<Unit> {
        SessionFixture().use { fixture ->
            fixture.login();fixture.rotationStarted=CompletableDeferred();fixture.releaseRotation=CompletableDeferred()
            val request=launch {
                fixture.repository.update(fixture.user.ownerKey,fixture.user.name,fixture.user.username,fixture.user.bio,"",
                    fixture.user.email,"Password2026","NewPassword2026")
            }
            withTimeout(5000) { fixture.rotationStarted!!.await() }
            request.cancel();fixture.releaseRotation!!.complete(Unit)
            withTimeout(5000) { request.join() }
            assertEquals("s".repeat(43),fixture.secret!!.refreshToken)
            fixture.repository.me(fixture.user.ownerKey)
            assertEquals(0,fixture.refreshes);assertEquals(1,fixture.protectedCalls)
        }
    }

    @Test fun revokedRefreshRequiresSignInButTransientFailurePreservesTheSession()=runBlocking<Unit> {
        SessionFixture().use { fixture ->
            fixture.login();val original=fixture.secret!!
            fixture.secret=original.copy(accessExpiresAt=0)
            fixture.failRefresh=java.io.IOException("Temporary connection failure")
            assertTrue(runCatching { fixture.repository.me(fixture.user.ownerKey) }.isFailure)
            assertNotNull(fixture.secret);assertEquals(true,fixture.repository.sessionValidity.value[fixture.user.ownerKey])
            fixture.failRefresh=LobbyApiException("SESSION_EXPIRED",401,"Sign in again to continue.")
            val failure=runCatching { fixture.repository.me(fixture.user.ownerKey) }.exceptionOrNull() as LobbyApiException
            assertEquals("SESSION_EXPIRED",failure.code);assertNull(fixture.secret)
            assertEquals(false,fixture.repository.sessionValidity.value[fixture.user.ownerKey])
            assertNotNull(fixture.db.archive().remoteAccount(fixture.user.ownerKey))
            fixture.failRefresh=null;fixture.login()
            assertEquals(true,fixture.repository.sessionValidity.value[fixture.user.ownerKey])
            fixture.repository.me(fixture.user.ownerKey)
            assertEquals(1,fixture.protectedCalls)
        }
    }

    @Test fun rejectedReplacementSessionDoesNotEnterAnEndlessRefreshLoop()=runBlocking<Unit> {
        SessionFixture().use { fixture ->
            fixture.login()
            fixture.failProtected=LobbyApiException("SESSION_EXPIRED",401,"Sign in again to continue.")
            val failure=runCatching { fixture.repository.me(fixture.user.ownerKey) }.exceptionOrNull() as LobbyApiException
            assertEquals("SESSION_EXPIRED",failure.code)
            assertEquals(1,fixture.refreshes);assertEquals(2,fixture.protectedCalls)
            assertNull(fixture.secret);assertEquals(false,fixture.repository.sessionValidity.value[fixture.user.ownerKey])
            assertTrue(runCatching { fixture.repository.me(fixture.user.ownerKey) }.isFailure)
            assertEquals(1,fixture.refreshes);assertEquals(2,fixture.protectedCalls)
        }
    }

    @Test fun followUsesJsonPayloadAndUnfollowKeepsItsDeleteContract()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MarvelDatabase::class.java).build()
        try {
            val user=remote();val target=remote()
            var secret: SessionSecrets?=null
            val tokens=object:OnlineTokens {
                override fun read(owner:String)=secret
                override fun save(owner:String,secrets:SessionSecrets) { secret=secrets }
                override fun remove(owner:String) { secret=null }
            }
            fun profile(person: UserProfile)=JSONObject().put("id",person.id).put("name",person.name)
                .put("username",person.username).put("email",person.email).put("bio",person.bio)
                .put("avatarId",JSONObject.NULL).put("joinedAt","2026-10-01T12:00:00Z")
            val calls=mutableListOf<Pair<String,JSONObject?>>()
            val transport=object:LobbyTransport {
                override val origin="https://example.invalid"
                override suspend fun request(method:String,path:String,body:JSONObject?,bearer:String?):JSONObject {
                    if(path=="/v1/auth/login")return JSONObject().put("user",profile(user)).put("accessToken","a".repeat(43))
                        .put("refreshToken","r".repeat(43)).put("accessExpiresAt","2036-10-01T12:00:00Z")
                    assertEquals("/v1/community/users/${target.id}/follow",path)
                    assertEquals("a".repeat(43),bearer);calls.add(method to body)
                    return JSONObject().put("profile",profile(target)).put("followers",if(method=="POST")1 else 0)
                        .put("following",0).put("isFollowing",method=="POST").put("followsYou",false).put("isSelf",false)
                }
            }
            val avatars=AvatarRepository(context)
            val accounts=OnlineAccountRepository(db.archive(),transport,tokens,avatars)
            accounts.login(user.email,"Password2026")
            val social=CommunityRepository(accounts,avatars)
            assertTrue(social.follow(user.ownerKey,target.id!!,true).isFollowing)
            assertEquals("POST",calls[0].first);assertNotNull(calls[0].second);assertEquals("{}",calls[0].second.toString())
            assertFalse(social.follow(user.ownerKey,target.id,false).isFollowing)
            assertEquals("DELETE",calls[1].first);assertNull(calls[1].second)
        } finally { db.close() }
    }

    @Test fun bindingMergesFavoritesAndHistoryWithoutLosingChatsOrRebinding()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MarvelDatabase::class.java).build()
        try {
            val dao=db.archive();val source="local@example.invalid";val target=remote();seed(dao,source)
            dao.save(StoredRecord().apply { owner=source;recordKey="CHARACTER:1";payload="local";favorite=true;viewedAt=20 })
            dao.save(StoredRecord().apply { owner=target.ownerKey;recordKey="CHARACTER:1";payload="remote";favorite=false;viewedAt=30 })
            dao.saveConversation(StoredConversation().apply { id="old-chat";owner=source;title="Keep this";payload="{}";updatedAt=10 })
            dao.bindLocalAccount(source,row(target));dao.bindLocalAccount(source,row(target))
            assertTrue(dao.records(source).isEmpty());assertTrue(dao.conversations(source).isEmpty())
            val merged=dao.records(target.ownerKey).single()
            assertTrue(merged.favorite);assertEquals(30L,merged.viewedAt);assertEquals("remote",merged.payload)
            assertEquals("Keep this",dao.conversations(target.ownerKey).single().title)
            assertTrue(runCatching { dao.bindLocalAccount(source,row(remote())) }.isFailure)
            assertEquals(target.ownerKey,dao.binding(source).remoteOwner)
            // Writes already queued under the old account must follow the committed binding.
            dao.updateRecord(source,"POWER:2","late record",false,40)
            dao.saveConversation(StoredConversation().apply { id="late-chat";owner=source;title="Late";payload="{}";updatedAt=40 })
            assertTrue(dao.records(source).isEmpty());assertTrue(dao.conversations(source).isEmpty())
            assertEquals(2,dao.records(target.ownerKey).size);assertEquals(2,dao.conversations(target.ownerKey).size)
            val another=remote();dao.saveRemoteAccount(row(another))
            dao.saveConversation(StoredConversation().apply { id="old-chat";owner=another.ownerKey;title="Overwrite";payload="{}";updatedAt=50 })
            assertTrue(dao.conversations(another.ownerKey).isEmpty())
        } finally { db.close() }
    }

    @Test fun versionThreeMigrationPreservesLocalIdentityAndRecords()=runBlocking<Unit> {
        val name="online-migration-${System.nanoTime()}.db"
        context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null).use { old ->
            old.execSQL("CREATE TABLE accounts (email TEXT NOT NULL PRIMARY KEY,name TEXT NOT NULL,passwordHash TEXT NOT NULL,salt TEXT NOT NULL,avatar TEXT NOT NULL,username TEXT NOT NULL DEFAULT '')")
            old.execSQL("CREATE UNIQUE INDEX index_accounts_username ON accounts(username)")
            old.execSQL("CREATE TABLE records (owner TEXT NOT NULL,recordKey TEXT NOT NULL,payload TEXT NOT NULL,favorite INTEGER NOT NULL,viewedAt INTEGER NOT NULL,PRIMARY KEY(owner,recordKey))")
            old.execSQL("CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY,owner TEXT NOT NULL,title TEXT NOT NULL,payload TEXT NOT NULL,updatedAt INTEGER NOT NULL)")
            old.execSQL("CREATE INDEX index_conversations_owner_updatedAt ON conversations(owner,updatedAt)")
            old.execSQL("INSERT INTO accounts VALUES ('old@example.invalid','Old hero','hash','salt','','old_hero')")
            old.execSQL("INSERT INTO records VALUES ('old@example.invalid','CHARACTER:1','{}',1,20)")
            old.execSQL("INSERT INTO conversations VALUES ('saved-chat','old@example.invalid','Hello','{}',20)")
            old.version=3
        }
        val db=Room.databaseBuilder(context,MarvelDatabase::class.java,name).addMigrations(MarvelDatabase.MIGRATION_3_4).build()
        try {
            val dao=db.archive()
            assertEquals("old_hero",dao.account("old@example.invalid").username)
            assertTrue(dao.records("old@example.invalid").single().favorite)
            assertEquals("Hello",dao.conversations("old@example.invalid").single().title)
            val profile=remote();dao.saveRemoteAccount(row(profile));assertEquals(profile.ownerKey,dao.remoteAccount(profile.ownerKey).owner)
            assertNull(dao.binding("old@example.invalid"))
        } finally { db.close();context.deleteDatabase(name) }
    }

    @Test fun tokensAreEncryptedSurviveReopeningAndCannotBeMovedToAnotherOwner() {
        val root=File(context.cacheDir,"online-token-test-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated=object:ContextWrapper(context) { override fun getFilesDir()=root }
        val user=remote();val secrets=SessionSecrets("https://example.invalid",user.id!!,"a".repeat(43),"b".repeat(43),Long.MAX_VALUE)
        try {
            OnlineTokenStore(isolated).save(user.ownerKey,secrets)
            val reopened=OnlineTokenStore(isolated)
            assertEquals(secrets,reopened.read(user.ownerKey))
            val saved=root.resolve("online-sessions").listFiles()!!.single()
            val bytes=saved.readBytes()
            assertFalse(String(bytes).contains(secrets.refreshToken));assertFalse(String(bytes).contains(secrets.accessToken))
            val other=remote();reopened.save(other.ownerKey,secrets.copy(userId=other.id!!))
            val otherFile=root.resolve("online-sessions").listFiles()!!.first { it.name!=saved.name }
            otherFile.writeBytes(bytes);assertNull(reopened.read(other.ownerKey))
            reopened.remove(user.ownerKey);assertNull(reopened.read(user.ownerKey))
        } finally { root.deleteRecursively() }
    }

    @Test fun protocolRejectsInvalidIdentityAndHidesUnknownServerDetails() {
        val user=remote()
        val data=JSONObject().put("id",user.id).put("name",user.name).put("username",user.username).put("bio",user.bio)
            .put("avatarId",JSONObject.NULL).put("joinedAt","2026-10-01T12:00:00Z")
        val parsed=LobbyProtocol.profile(data) { "" }
        assertEquals("",parsed.email);assertEquals(user.id,parsed.id)
        assertTrue(runCatching { LobbyProtocol.profile(JSONObject(data.toString()).put("id","not-a-uuid")) { "" } }.isFailure)
        val failure=LobbyProtocol.failure(500,"<html>private SQL/password</html>")
        assertEquals("The online service is unavailable. Please try again.",failure.message)
    }

    @Test fun expiredAccessRefreshesOnceAndRetiredLogoutCannotRemoveNewSession()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MarvelDatabase::class.java).build()
        try {
            val user=remote();var token:SessionSecrets?=null
            val store=object:OnlineTokens {
                override fun read(owner:String)=token
                override fun save(owner:String,secrets:SessionSecrets) { token=secrets }
                override fun remove(owner:String) { token=null }
            }
            var refreshes=0;val calls=mutableListOf<String>()
            val profile=JSONObject().put("id",user.id).put("name",user.name).put("email",user.email).put("username",user.username)
                .put("bio",user.bio).put("avatarId",JSONObject.NULL).put("joinedAt","2026-10-01T12:00:00Z")
            fun session(access:String)=JSONObject().put("user",profile).put("accessToken",access.repeat(43)).put("refreshToken","r".repeat(43))
                .put("accessExpiresAt","2036-10-01T12:00:00Z").put("refreshExpiresAt","2036-10-30T12:00:00Z")
            val api=object:LobbyTransport {
                override val origin="https://example.invalid"
                override suspend fun request(method:String,path:String,body:JSONObject?,bearer:String?):JSONObject {
                    calls.add(path)
                    return when(path) {
                        "/v1/auth/login" -> session("a")
                        "/v1/auth/refresh" -> { refreshes++;session("b") }
                        "/v1/auth/logout" -> JSONObject()
                        else -> profile
                    }
                }
            }
            val repository=OnlineAccountRepository(db.archive(),api,store,AvatarRepository(context))
            assertEquals(user.ownerKey,repository.login(user.email,"Password2026").ownerKey)
            token=token!!.copy(accessExpiresAt=0)
            assertEquals(user.id,repository.me(user.ownerKey).id);assertEquals(1,refreshes)
            repository.me(user.ownerKey);assertEquals(1,refreshes)
            val retired=repository.retire(user.ownerKey)!!;assertNull(token)
            repository.login(user.email,"Password2026");val latest=token
            repository.revoke(retired.copy(accessExpiresAt=0));assertEquals(latest,token);assertEquals(2,refreshes)
            assertEquals("/v1/auth/logout",calls.last())
        } finally { db.close() }
    }
}
