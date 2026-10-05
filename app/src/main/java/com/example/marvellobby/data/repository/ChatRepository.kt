package com.example.marvellobby.data.repository

import com.example.marvellobby.data.local.*
import com.example.marvellobby.data.model.ComicEntity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChatSnapshot(val id: String,val title: String,val context: ComicEntity?,
    val messages: List<ChatMessage>,val sources: List<ComicEntity>)
data class ChatSummary(val id: String,val title: String,val updatedAt: Long)

class ChatRepository(private val dao: ArchiveDao) {
    private val gson=Gson()
    suspend fun save(owner: String,snapshot: ChatSnapshot) = withContext(Dispatchers.IO) {
        if(owner.isBlank() || snapshot.messages.isEmpty())return@withContext
        dao.saveConversation(StoredConversation().apply {
            id=snapshot.id;this.owner=owner;title=snapshot.title
            payload=gson.toJson(snapshot);updatedAt=System.currentTimeMillis()
        })
    }
    suspend fun list(owner: String)=withContext(Dispatchers.IO) {
        dao.conversations(owner).map { ChatSummary(it.id,it.title,it.updatedAt) }
    }
    suspend fun load(owner: String,id: String): ChatSnapshot?=withContext(Dispatchers.IO) {
        dao.conversation(owner,id)?.let { runCatching { gson.fromJson(it.payload,ChatSnapshot::class.java) }.getOrNull() }
    }
    suspend fun delete(owner: String,id: String)=withContext(Dispatchers.IO) { dao.deleteConversation(owner,id) }
}
