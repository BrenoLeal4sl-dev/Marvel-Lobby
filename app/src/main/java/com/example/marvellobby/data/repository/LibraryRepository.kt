package com.example.marvellobby.data.repository

import com.example.marvellobby.data.local.*
import com.example.marvellobby.data.model.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LibraryItem(val entity: ComicEntity, val favorite: Boolean, val viewedAt: Long)
class LibraryRepository(private val dao: ArchiveDao) {
    private val gson=Gson()
    private val mutex=Mutex()
    private fun key(e: ComicEntity)="${e.type.name}:${e.id}"
    suspend fun all(owner: String): List<LibraryItem> = withContext(Dispatchers.IO) {
        dao.records(owner).mapNotNull { row ->
            runCatching { LibraryItem(gson.fromJson(row.payload,ComicEntity::class.java),row.favorite,row.viewedAt) }.getOrNull()
        }
    }
    suspend fun record(owner: String, entity: ComicEntity, toggleFavorite: Boolean=false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            dao.updateRecord(owner,key(entity),gson.toJson(entity),toggleFavorite,System.currentTimeMillis())
        }
    }
    suspend fun clearHistory(owner: String) = withContext(Dispatchers.IO) {
        mutex.withLock { dao.deleteHistory(owner); dao.resetHistory(owner) }
    }
}
