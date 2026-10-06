package com.example.marvellobby

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.marvellobby.data.local.*
import com.example.marvellobby.data.model.*
import com.example.marvellobby.data.repository.*
import com.example.marvellobby.data.remote.*
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray

@RunWith(AndroidJUnit4::class)
class CloudPersistenceTest {
    @Test fun twoDevicesSyncHistoryAndChatsAndPropagateDeletionThroughAuthenticatedTransport()=runBlocking<Unit> {
        val first=Room.inMemoryDatabaseBuilder(context,MarvelDatabase::class.java).build()
        val second=Room.inMemoryDatabaseBuilder(context,MarvelDatabase::class.java).build()
        try {
            var enabled=false;var revision=0L;val records=mutableMapOf<String,JSONObject>()
            val transport=object:LobbyTransport {
                override val origin="https://example.invalid"
                override suspend fun request(method:String,path:String,body:JSONObject?,bearer:String?):JSONObject {
                    assertEquals("a".repeat(43),bearer)
                    if(path=="/v1/archive/settings") {
                        if(method=="PUT")enabled=body!!.getBoolean("enabled")
                        return JSONObject().put("enabled",enabled)
                    }
                    check(enabled)
                    if(method=="GET") {
                        val cursor=path.substringAfter("after=").toLong()
                        val rows=records.values.filter { it.getString("revision").toLong()>cursor }.sortedBy { it.getString("revision").toLong() }
                        return JSONObject().put("items",JSONArray(rows.take(1))).put("more",rows.size>1)
                    }
                    val key=body!!.getString("key");val prior=records[key]
                    if(prior!=null && prior.getString("nonce")==body.getString("nonce"))return JSONObject().put("accepted",true).put("record",prior)
                    if((prior?.getString("revision") ?: "0")!=body.getString("base"))return JSONObject().put("accepted",false).put("record",prior)
                    val saved=JSONObject(body.toString()).put("revision",(++revision).toString());records[key]=saved
                    return JSONObject().put("accepted",true).put("record",saved)
                }
            }
            fun repository(db:MarvelDatabase):CloudSyncRepository {
                db.archive().saveRemoteAccount(RemoteAccount().apply { this.owner=this@CloudPersistenceTest.owner;payload="{}" })
                val secrets=SessionSecrets(transport.origin,owner.removePrefix("remote:"),"a".repeat(43),"r".repeat(43),Long.MAX_VALUE)
                val tokens=object:OnlineTokens {
                    override fun read(owner:String)=secrets
                    override fun save(owner:String,secrets:SessionSecrets) {}
                    override fun remove(owner:String) {}
                }
                return CloudSyncRepository(db.archive(),OnlineAccountRepository(db.archive(),transport,tokens,AvatarRepository(context)))
            }
            val a=repository(first);val b=repository(second);assertFalse(a.enabled(owner));a.choose(owner,true)
            record(first.archive())
            val id=UUID.randomUUID().toString();val snapshot=ChatSnapshot(id,"Question",null,listOf(ChatMessage("user","Quem é o Homem-Aranha?")),emptyList())
            ChatRepository(first.archive()).save(owner,snapshot)
            a.sync(owner,"Preserved");b.sync(owner,"Preserved")
            assertEquals("Spider-Man",LibraryRepository(second.archive()).all(owner).single().entity.name)
            assertEquals(snapshot.messages,ChatRepository(second.archive()).load(owner,id)!!.messages)
            // Divergent edits must become separate conversations, never overwrite either answer.
            ChatRepository(first.archive()).save(owner,snapshot.copy(messages=snapshot.messages+ChatMessage("model","Peter Parker")))
            ChatRepository(second.archive()).save(owner,snapshot.copy(messages=snapshot.messages+ChatMessage("model","Miles Morales")))
            a.sync(owner,"Preserved");assertEquals(1,b.sync(owner,"Preserved"));a.sync(owner,"Preserved")
            assertEquals(2,first.archive().conversations(owner).size);assertEquals(2,second.archive().conversations(owner).size)
            LibraryRepository(second.archive()).clearHistory(owner);ChatRepository(second.archive()).delete(owner,id)
            b.sync(owner,"Preserved");a.sync(owner,"Preserved")
            assertFalse(LibraryRepository(first.archive()).all(owner).any { it.viewedAt>0 })
            assertNull(ChatRepository(first.archive()).load(owner,id));assertEquals(0,a.pending(owner))
        } finally { first.close();second.close() }
    }
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val owner="remote:11111111-1111-4111-8111-111111111111"
    private val other="remote:22222222-2222-4222-8222-222222222222"
    private fun record(dao: ArchiveDao) {
        val entity=ComicEntity(1443,ResourceType.CHARACTER,"Spider-Man",null,null,null,null,null,0,null,null,null,emptyList(),emptyList(),emptyList(),emptyList())
        dao.updateRecord(owner,"CHARACTER:1443",Gson().toJson(entity),false,1000)
    }
    @Test fun offlineDeletionsSurviveRestartAndLateAckDoesNotLoseANewerEdit() {
        val name="cloud-${UUID.randomUUID()}.db";var db=Room.databaseBuilder(context,MarvelDatabase::class.java,name).build()
        try {
            val dao=db.archive();record(dao);val old=dao.cloudChanges(owner).single()
            record(dao);dao.acknowledgeCloud(owner,old.recordKey,old.nonce!!,10)
            assertTrue(dao.cloudChanges(owner).single().pending);assertEquals(10L,dao.cloudChanges(owner).single().revision)
            dao.updateRecord(owner,"CHARACTER:1443",dao.record(owner,"CHARACTER:1443").payload,true,0)
            dao.clearHistoryWithSync(owner)
            assertTrue(dao.record(owner,"CHARACTER:1443").favorite);assertEquals(0L,dao.record(owner,"CHARACTER:1443").viewedAt)
            db.close();db=Room.databaseBuilder(context,MarvelDatabase::class.java,name).build()
            assertNull(db.archive().cloudChanges(owner).single().payload)
            assertTrue(db.archive().cloudChanges(other).isEmpty())
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun conflictsPreserveBothChatsAndImportsNeverUploadAnotherUsersData()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,MarvelDatabase::class.java).build()
        try {
            val dao=db.archive();dao.saveRemoteAccount(RemoteAccount().apply { this.owner=this@CloudPersistenceTest.owner;payload="{}" })
            val id=UUID.randomUUID().toString();val snapshot=ChatSnapshot(id,"Local",null,listOf(ChatMessage("user","Local question")),emptyList())
            ChatRepository(dao).save(owner,snapshot);val change=dao.cloudChanges(owner).single()
            val remote=Gson().toJson(snapshot.copy(title="Remote",messages=listOf(ChatMessage("user","Remote question"))))
            val (key,branch)=CloudPayload.fork(change.payload!!,"Preserved")
            dao.forkCloud(owner,change.recordKey,change.nonce!!,key,branch,remote,20)
            assertEquals(2,dao.conversations(owner).size);assertEquals("Remote",dao.conversation(owner,id).title)
            assertEquals(key,dao.cloudChanges(owner).single().recordKey)
            assertTrue(dao.conversations(other).isEmpty());assertTrue(dao.cloudChanges(other).isEmpty())
            assertThrows(IllegalStateException::class.java) { dao.importCloud(other,"chat:$id",remote,21,null) }
            val pending=dao.cloudChanges(owner).single()
            assertFalse(dao.importCloud(owner,key,null,30,null))
            assertNotNull(dao.conversation(owner,key.substring(5)))
            dao.acknowledgeCloud(owner,key,pending.nonce!!,31);dao.importCloud(owner,key,null,32,null)
            assertNull(dao.conversation(owner,key.substring(5)))
        } finally { db.close() }
    }
}
