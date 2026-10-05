package com.example.marvellobby.data.repository

import com.example.marvellobby.data.api.ComicVineApi
import com.example.marvellobby.data.api.ComicVineRequests
import com.example.marvellobby.data.api.ComicVineException
import com.example.marvellobby.data.api.ComicVineSource
import com.example.marvellobby.data.model.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

data class LoadedRecord(val entity: ComicEntity, val offline: Boolean = false)
data class SearchResults(val items: List<ComicEntity>, val offline: Boolean, val partial: Boolean)
data class LoadedIssues(val items: List<ComicEntity>,val offline: Boolean=false)

class MarvelRepository(private val api: ComicVineSource = ComicVineApi(), private val cacheDirectory: File? = null) {
    private val gson = Gson()
    private val lock = Mutex()
    private val ttl = 10 * 60 * 1000L
    private fun pageKey(type: ResourceType, query: String, offset: Int, sort: String, marvelOnly: Boolean) =
        "page-v4:$type:${query.trim().lowercase(java.util.Locale.ROOT)}:$offset:$sort:$marvelOnly"
    private fun file(key: String): File? = cacheDirectory?.let { dir ->
        dir.mkdirs()
        File(dir, MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) } + ".json")
    }
    private suspend fun <T> cached(key: String, type: Class<T>, fresh: Boolean): T? = lock.withLock {
        val f = file(key) ?: return@withLock null
        if (!f.exists() || (fresh && System.currentTimeMillis() - f.lastModified() > ttl)) return@withLock null
        runCatching { gson.fromJson(f.readText(), type) }.getOrNull()
    }
    private suspend fun save(key: String, data: Any) = lock.withLock {
        runCatching {
            file(key)?.let { target ->
                val temp = File(target.path + ".tmp")
                temp.writeText(gson.toJson(data))
                java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                cacheDirectory?.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }?.drop(200)?.forEach { it.delete() }
            }
        }
        Unit
    }
    suspend fun browse(type: ResourceType, query: String = "", offset: Int = 0, sort: String = ComicVineRequests.RECENT, marvelOnly: Boolean = true): ComicPage = withContext(Dispatchers.IO) {
        val key = pageKey(type,query,offset,sort,marvelOnly)
        cached(key, ComicPage::class.java, true)?.let { return@withContext it }
        try {
            var next = offset
            var result: ComicPage
            var scanned = 0
            var progressed: Boolean
            do {
                ensureActive()
                val raw = api.list(type, if(type==ResourceType.POWER) PowerNames.query(query) else query, next, 40, sort)
                result = raw.copy(items = if (type == ResourceType.POWER || !marvelOnly) raw.items else raw.items.filter { it.isMarvel })
                progressed = raw.nextOffset > next
                next = raw.nextOffset
                scanned++
            } while (result.items.isEmpty() && result.hasMore && progressed && scanned < 3)
            save(key, result)
            result
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            ensureActive()
            cached(key, ComicPage::class.java, false)?.copy(offline = true) ?: throw error
        }
    }
    suspend fun loadDetail(type: ResourceType, id: Int): LoadedRecord = withContext(Dispatchers.IO) {
        val key = "detail-v3:$type:$id"
        cached(key, ComicEntity::class.java, true)?.let { return@withContext LoadedRecord(it) }
        try {
            val entity = api.detail(type,id)
            ensureActive()
            save(key,entity)
            LoadedRecord(entity)
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: Exception) {
            ensureActive()
            (cached(key,ComicEntity::class.java,false)
                ?: cached("detail-v2:$type:$id",ComicEntity::class.java,false))?.let { LoadedRecord(it,true) } ?: throw error
        }
    }
    suspend fun detail(type: ResourceType, id: Int) = loadDetail(type,id).entity
    suspend fun issueSummaries(ids: List<Int>): LoadedIssues = withContext(Dispatchers.IO) {
        val selected=ids.distinct().sorted()
        require(selected.size in 1..100 && selected.all { it>0 })
        val key="issue-summaries-v1:${selected.joinToString(",") }"
        cached(key,LoadedIssues::class.java,true)?.let { return@withContext it }
        try {
            val records=api.issues(selected).filter { it.type==ResourceType.ISSUE && it.id in selected }
            ensureActive()
            LoadedIssues(records).also { save(key,it) }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: Exception) {
            ensureActive()
            cached(key,LoadedIssues::class.java,false)?.copy(offline=true) ?: throw error
        }
    }
    suspend fun searchResults(query: String): SearchResults {
        val results = mutableListOf<Result<ComicPage>>()
        var accessFailure: Exception? = null
        for (type in listOf(ResourceType.CHARACTER,ResourceType.TEAM,ResourceType.POWER,ResourceType.STORY_ARC)) {
            currentCoroutineContext().ensureActive()
            if (accessFailure != null) {
                val saved = withContext(Dispatchers.IO) {
                    cached(pageKey(type,query,0,ComicVineRequests.RECENT,true),ComicPage::class.java,false)
                }
                if (saved != null) results.add(Result.success(saved.copy(offline=true)))
                else results.add(Result.failure(accessFailure))
                continue
            }
            try { results.add(Result.success(browse(type,query))) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(error: Exception) {
                results.add(Result.failure(error))
                // Stop network requests, but still search already-cached categories.
                if (error is ComicVineException && error.failure.stopsRequests) accessFailure = error
            }
        }
        val pages = results.mapNotNull { it.getOrNull() }
        if(pages.isEmpty()) throw results.first().exceptionOrNull()!!
        return SearchResults(pages.flatMap { it.items },pages.any { it.offline },results.any { it.isFailure })
    }
    suspend fun search(query: String): List<ComicEntity> = searchResults(query).items
}
