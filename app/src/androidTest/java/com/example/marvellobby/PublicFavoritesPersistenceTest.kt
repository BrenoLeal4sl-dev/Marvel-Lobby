package com.example.marvellobby

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.marvellobby.data.local.*
import com.example.marvellobby.data.model.*
import com.example.marvellobby.data.remote.*
import com.example.marvellobby.data.repository.*
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PublicFavoritesPersistenceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val owner="remote:11111111-1111-4111-8111-111111111111"
    private fun toggle(dao:ArchiveDao,id:Int=1) {
        val entity=ComicEntity(id,ResourceType.CHARACTER,"Spider-Man",null,null,null,null,null,0,null,null,null,emptyList(),emptyList(),emptyList(),emptyList())
        dao.updateRecord(owner,"CHARACTER:$id",Gson().toJson(entity),true,0)
    }
    @Test fun versionFourMigrationPreservesAccountsLibraryChatsAndBindings() {
        val name="favorites-v4-${UUID.randomUUID()}.db"
        context.openOrCreateDatabase(name,android.content.Context.MODE_PRIVATE,null).use { old ->
            old.execSQL("CREATE TABLE accounts(email TEXT NOT NULL PRIMARY KEY,name TEXT NOT NULL,passwordHash TEXT NOT NULL,salt TEXT NOT NULL,avatar TEXT NOT NULL,username TEXT NOT NULL DEFAULT '')")
            old.execSQL("CREATE UNIQUE INDEX index_accounts_username ON accounts(username)")
            old.execSQL("CREATE TABLE records(owner TEXT NOT NULL,recordKey TEXT NOT NULL,payload TEXT NOT NULL,favorite INTEGER NOT NULL,viewedAt INTEGER NOT NULL,PRIMARY KEY(owner,recordKey))")
            old.execSQL("CREATE TABLE conversations(id TEXT NOT NULL PRIMARY KEY,owner TEXT NOT NULL,title TEXT NOT NULL,payload TEXT NOT NULL,updatedAt INTEGER NOT NULL)")
            old.execSQL("CREATE INDEX index_conversations_owner_updatedAt ON conversations(owner,updatedAt)")
            old.execSQL("CREATE TABLE remote_accounts(owner TEXT NOT NULL PRIMARY KEY,payload TEXT NOT NULL)")
            old.execSQL("CREATE TABLE account_bindings(localOwner TEXT NOT NULL PRIMARY KEY,remoteOwner TEXT NOT NULL)")
            old.execSQL("INSERT INTO accounts VALUES('local@example.invalid','Hero','hash','salt','','hero')")
            old.execSQL("INSERT INTO records VALUES(?, 'CHARACTER:1','{}',1,123)",arrayOf(owner))
            old.execSQL("INSERT INTO conversations VALUES('old-chat',?,'Saved conversation','{}',123)",arrayOf(owner))
            old.execSQL("INSERT INTO remote_accounts VALUES(?,'{}')",arrayOf(owner))
            old.execSQL("INSERT INTO account_bindings VALUES('local@example.invalid',?)",arrayOf(owner))
            old.version=4
        }
        val db=Room.databaseBuilder(context,MarvelDatabase::class.java,name).addMigrations(MarvelDatabase.MIGRATION_4_5,MarvelDatabase.MIGRATION_5_6).build()
        try {
            assertEquals("",db.archive().account("local@example.invalid").bio)
            assertTrue(db.archive().records(owner).single().favorite)
            assertEquals("Saved conversation",db.archive().conversations(owner).single().title)
            assertEquals(owner,db.archive().binding("local@example.invalid").remoteOwner)
            assertTrue(db.archive().publicChanges(owner).isEmpty())
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun pendingRemovalSurvivesRestartAndLateAcknowledgmentCannotEraseANewerChange() {
        val name="favorite-outbox-${UUID.randomUUID()}.db"
        var db=Room.databaseBuilder(context,MarvelDatabase::class.java,name).build()
        try {
            toggle(db.archive());val sent=db.archive().publicChanges(owner).single()
            toggle(db.archive());db.archive().acknowledgePublicChange(owner,sent.recordKey,sent.nonce)
            assertFalse(db.archive().publicChanges(owner).single().favorite)
            db.close();db=Room.databaseBuilder(context,MarvelDatabase::class.java,name).build()
            val pending=db.archive().publicChanges(owner).single();assertFalse(pending.favorite)
            assertTrue(db.archive().publicChanges("remote:22222222-2222-4222-8222-222222222222").isEmpty())
            db.archive().queueFavoriteSharing(owner,true)
            assertFalse(db.archive().publicChanges(owner).single().favorite)
            db.archive().acknowledgePublicChange(owner,pending.recordKey,pending.nonce)
            assertTrue(db.archive().publicChanges(owner).isEmpty())
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun failedUploadStaysQueuedAndSharingOnlyStartsAfterSuccessfulRetry()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MarvelDatabase::class.java).build()
        try {
            var enabled=false;var fail=true;val operations=mutableListOf<String>()
            val secrets=SessionSecrets("https://example.invalid",owner.removePrefix("remote:"),"a".repeat(43),"r".repeat(43),Long.MAX_VALUE)
            val tokens=object:OnlineTokens {
                override fun read(owner:String)=secrets
                override fun save(owner:String,secrets:SessionSecrets) {}
                override fun remove(owner:String) {}
            }
            val transport=object:LobbyTransport {
                override val origin="https://example.invalid"
                override suspend fun request(method:String,path:String,body:JSONObject?,bearer:String?):JSONObject {
                    operations.add("$method $path")
                    if(path.endsWith("/favorites")) {
                        if(fail)throw IOException("Simulated offline connection")
                        val item=body!!.getJSONArray("items").getJSONObject(0)
                        assertEquals(setOf("type","id","name","imageUrl","favorite"),item.keys().asSequence().toSet())
                        return JSONObject().put("ok",true)
                    }
                    if(method=="PUT")enabled=body!!.getBoolean("enabled")
                    return JSONObject().put("enabled",enabled)
                }
            }
            val accounts=OnlineAccountRepository(db.archive(),transport,tokens,AvatarRepository(context))
            val repo=PublicFavoritesRepository(db.archive(),accounts)
            toggle(db.archive());repo.chooseSharing(owner,true)
            assertTrue(runCatching { repo.sync(owner) }.isFailure)
            assertFalse(enabled);assertEquals(1,db.archive().publicChanges(owner).size)
            assertNotNull(db.archive().sharingChange(owner))
            operations.clear();fail=false;assertTrue(repo.sync(owner))
            assertTrue(operations.indexOf("POST /v1/community/me/favorites")<operations.indexOf("PUT /v1/community/me/favorite-sharing"))
            assertTrue(db.archive().publicChanges(owner).isEmpty());assertNull(db.archive().sharingChange(owner))
            toggle(db.archive());repo.chooseSharing(owner,false);operations.clear()
            assertFalse(repo.sync(owner));assertEquals(listOf("PUT /v1/community/me/favorite-sharing"),operations)
            assertFalse(db.archive().publicChanges(owner).single().favorite)
        } finally { db.close() }
    }
}
