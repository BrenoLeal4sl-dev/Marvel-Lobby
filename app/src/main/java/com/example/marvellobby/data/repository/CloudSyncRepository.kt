package com.example.marvellobby.data.repository

import com.example.marvellobby.data.local.ArchiveDao
import com.example.marvellobby.data.local.CloudRecord
import com.example.marvellobby.data.model.*
import com.google.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/** Cloud copies are compact catalog snapshots; AI messages retain their full text. */
object CloudPayload {
    private val gson=Gson()
    private fun entity(json: JsonElement): ComicEntity {
        val source=json.asJsonObject
        val id=source.get("id").asInt;val type=ResourceType.valueOf(source.get("type").asString)
        require(id>0)
        fun text(key: String)=source.get(key)?.takeUnless { it.isJsonNull }?.asString
        fun refs(key: String)=source.getAsJsonArray(key)?.map { gson.fromJson(it,ComicReference::class.java) }?.filter { it.id>0 && it.name.isNotBlank() }?.take(200).orEmpty()
        val image=text("imageUrl")?.takeIf { url->runCatching {
            val uri=java.net.URI(url);uri.scheme=="https" && uri.userInfo==null && uri.port==-1 &&
                uri.host?.matches(Regex("(comicvine\\.gamespot\\.com|comicvine\\d*\\.cbsistatic\\.com|static\\.comicvine\\.com)"))==true
        }.getOrDefault(false) }
        return ComicEntity(id,type,text("name")?.take(1000) ?: throw IOException("Invalid cloud record."),text("realName"),
            text("summary")?.take(1200),null,image,source.get("publisher")?.takeUnless { it.isJsonNull }?.let { gson.fromJson(it,ComicReference::class.java) },
            source.get("appearanceCount")?.asInt ?: 0,text("aliases")?.take(1200),text("origin"),
            source.get("firstIssue")?.takeUnless { it.isJsonNull }?.let { gson.fromJson(it,ComicReference::class.java) },
            refs("powers"),refs("teams"),emptyList(),emptyList(),issueNumber=text("issueNumber"),
            volume=source.get("volume")?.takeUnless { it.isJsonNull }?.let { gson.fromJson(it,ComicReference::class.java) })
    }
    fun compact(key: String,payload: String?): String? {
        if(payload==null)return null
        val json=JsonParser.parseString(payload).asJsonObject
        if(key.startsWith("history:")) {
            val record=entity(json.get("entity"));require(key=="history:${record.type}:${record.id}")
            require(json.get("viewedAt").asLong>0)
            json.add("entity",gson.toJsonTree(record))
        } else {
            require(key=="chat:${json.get("id").asString}");UUID.fromString(json.get("id").asString)
            val messages=json.getAsJsonArray("messages");require(messages.size()<=2000)
            messages.forEach { item->val m=item.asJsonObject;require(m.get("role").asString in setOf("user","model") && m.get("text").asString.length<=64000) }
            require(json.get("title").asString.length in 1..500)
            json.get("context")?.takeUnless { it.isJsonNull }?.let { json.add("context",gson.toJsonTree(entity(it))) }
            val sources=JsonArray();json.getAsJsonArray("sources").forEach { sources.add(gson.toJsonTree(entity(it))) };require(sources.size()<=100)
            json.add("sources",sources)
        }
        return json.toString().also { require(it.length<=800_000) { "This conversation is too large to sync. It remains saved on this device." } }
    }
    /** A longer common transcript is safe to merge; divergent branches are kept separately. */
    fun compatibleChats(local: String,remote: String): Boolean {
        val a=JsonParser.parseString(local).asJsonObject;val b=JsonParser.parseString(remote).asJsonObject
        if(a.get("context")!=b.get("context"))return false
        val left=a.getAsJsonArray("messages");val right=b.getAsJsonArray("messages")
        return (0 until minOf(left.size(),right.size())).all { left[it]==right[it] }
    }
    fun messageCount(payload: String)=JsonParser.parseString(payload).asJsonObject.getAsJsonArray("messages").size()
    fun viewedAt(payload: String)=JsonParser.parseString(payload).asJsonObject.get("viewedAt").asLong
    fun fork(payload: String,title: String): Pair<String,String> {
        val json=JsonParser.parseString(payload).asJsonObject;val id=UUID.randomUUID().toString()
        json.addProperty("id",id);json.addProperty("title",json.get("title").asString.take(440)+" · "+title)
        return "chat:$id" to json.toString()
    }
}

class CloudSyncRepository(private val dao: ArchiveDao,private val accounts: OnlineAccountRepository) {
    val changes=kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity=16)
    val changedChats=kotlinx.coroutines.flow.MutableSharedFlow<Pair<String,String>>(extraBufferCapacity=32)
    private val lock=Mutex()
    suspend fun enabled(owner: String)=accounts.archiveRequest(owner,"GET","/v1/archive/settings").getBoolean("enabled")
    suspend fun choose(owner: String,enabled: Boolean)=withContext(Dispatchers.IO) { lock.withLock {
        accounts.archiveRequest(owner,"PUT","/v1/archive/settings",JSONObject().put("enabled",enabled))
        if(enabled)dao.seedCloud(owner)
    } }
    suspend fun sync(owner: String,recoveredTitle: String): Int=withContext(Dispatchers.IO) { lock.withLock {
        var forks=0
        // Bounded work avoids a large offline queue monopolizing the UI's sync action.
        for(batch in 0 until 2) {
            currentCoroutineContext().ensureActive()
            val changes=dao.cloudChanges(owner)
            if(changes.isEmpty())break
            for(change in changes) {
                val payload=CloudPayload.compact(change.recordKey,change.payload)
                val body=JSONObject().put("key",change.recordKey).put("scope",change.recordKey.substringBefore(':'))
                    .put("base",change.revision.toString()).put("nonce",change.nonce)
                    .put("payload",payload?.let(::JSONObject) ?: JSONObject.NULL)
                val result=accounts.archiveRequest(owner,"PUT","/v1/archive",body)
                val record=result.getJSONObject("record");val revision=record.getString("revision").toLong().also { require(it>0) }
                if(result.getBoolean("accepted"))dao.acknowledgeCloud(owner,change.recordKey,change.nonce!!,revision)
                else {
                    val remote=record.optJSONObject("payload")?.toString()?.let { CloudPayload.compact(change.recordKey,it) }
                    when {
                        payload==null -> dao.rebaseCloud(owner,change.recordKey,change.nonce!!,revision,null)
                        change.recordKey.startsWith("history:") && remote!=null && CloudPayload.viewedAt(payload)>=CloudPayload.viewedAt(remote) ->
                            dao.rebaseCloud(owner,change.recordKey,change.nonce!!,revision,payload)
                        change.recordKey.startsWith("chat:") && remote!=null && CloudPayload.compatibleChats(payload,remote) && CloudPayload.messageCount(payload)>CloudPayload.messageCount(remote) ->
                            dao.rebaseCloud(owner,change.recordKey,change.nonce!!,revision,payload)
                        change.recordKey.startsWith("chat:") && (remote==null || !CloudPayload.compatibleChats(payload,remote)) -> {
                            val (key,branch)=CloudPayload.fork(payload,recoveredTitle)
                            if(dao.forkCloud(owner,change.recordKey,change.nonce!!,key,branch,remote,revision)) {
                                forks++;changedChats.tryEmit(owner to change.recordKey.substring(5))
                            }
                        }
                        else -> if(dao.importCloud(owner,change.recordKey,remote,revision,change.nonce) && change.recordKey.startsWith("chat:"))
                            changedChats.tryEmit(owner to change.recordKey.substring(5))
                    }
                }
            }
        }
        var cursor=dao.cloudRecord(owner,"@cursor")?.revision ?: 0L
        // Cursor advances only after the local transaction imports the entire page.
        repeat(40) {
            currentCoroutineContext().ensureActive()
            val page=accounts.archiveRequest(owner,"GET","/v1/archive?after=$cursor")
            val items=page.getJSONArray("items")
            for(index in 0 until items.length()) {
                val record=items.getJSONObject(index);val key=record.getString("key")
                require(key.startsWith("history:") || key.startsWith("chat:"))
                val revision=record.getString("revision").toLong();require(revision>cursor)
                val payload=record.optJSONObject("payload")?.toString()?.let { CloudPayload.compact(key,it) }
                if(dao.importCloud(owner,key,payload,revision,null)) {
                    changes.tryEmit(owner)
                    if(key.startsWith("chat:"))changedChats.tryEmit(owner to key.substring(5))
                }
                cursor=revision
                dao.saveCloud(CloudRecord().apply { this.owner=owner;recordKey="@cursor";this.revision=cursor })
            }
            if(!page.getBoolean("more")) { changes.tryEmit(owner);return@withLock forks }
        }
        changes.tryEmit(owner)
        forks
    } }
    suspend fun pending(owner: String)=withContext(Dispatchers.IO) { dao.cloudPendingCount(owner) }
}
