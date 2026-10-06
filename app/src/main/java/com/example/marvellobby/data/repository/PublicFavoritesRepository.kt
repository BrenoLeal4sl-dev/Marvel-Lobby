package com.example.marvellobby.data.repository

import com.example.marvellobby.data.local.ArchiveDao
import com.example.marvellobby.data.model.*
import com.example.marvellobby.data.remote.PublicFavoritesProtocol
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Public catalog projections only. Favorites stay usable locally when the server is offline. */
class PublicFavoritesRepository(private val dao: ArchiveDao,private val accounts: OnlineAccountRepository) {
    private val mutex=Mutex()
    private val gson=Gson()
    suspend fun chooseSharing(owner: String,enabled: Boolean)=withContext(Dispatchers.IO) {
        dao.queueFavoriteSharing(owner,enabled)
    }
    suspend fun sync(owner: String): Boolean=withContext(Dispatchers.IO) {
        mutex.withLock {
            val choice=dao.sharingChange(owner)
            val path="/v1/community/me/favorite-sharing"
            // Hide first; publish only after the existing library and queued removals are uploaded.
            if(choice?.favorite==false) {
                val enabled=accounts.communityRequest(owner,"PUT",path,JSONObject().put("enabled",false)).getBoolean("enabled")
                check(!enabled)
                dao.acknowledgePublicChange(owner,choice.recordKey,choice.nonce)
                return@withLock false
            }
            val enabled=accounts.communityRequest(owner,"GET",path).getBoolean("enabled")
            if(!enabled && choice?.favorite!=true)return@withLock false
            while(true) {
                currentCoroutineContext().ensureActive()
                val rows=dao.publicChanges(owner)
                if(rows.isEmpty())break
                val items=JSONArray()
                rows.forEach { row ->
                    val entity=gson.fromJson(row.payload,ComicEntity::class.java)
                    require(entity.type.canFavorite && row.recordKey=="${entity.type.name}:${entity.id}")
                    val name=entity.name.trim().let { it.substring(0,it.offsetByCodePoints(0,it.codePointCount(0,it.length).coerceAtMost(200))) }
                    items.put(JSONObject().put("type",entity.type.resource).put("id",entity.id).put("name",name)
                        .put("imageUrl",entity.imageUrl?.takeIf { it.length<=2048 } ?: JSONObject.NULL).put("favorite",row.favorite))
                }
                accounts.communityRequest(owner,"POST","/v1/community/me/favorites",JSONObject().put("items",items))
                rows.forEach { dao.acknowledgePublicChange(owner,it.recordKey,it.nonce) }
            }
            if(choice?.favorite==true) {
                // A later visibility choice must win over an older upload still in flight.
                if(dao.sharingChange(owner)?.nonce!=choice.nonce)return@withLock enabled
                val result=accounts.communityRequest(owner,"PUT",path,JSONObject().put("enabled",true)).getBoolean("enabled")
                check(result);dao.acknowledgePublicChange(owner,choice.recordKey,choice.nonce)
                true
            } else enabled
        }
    }
    suspend fun list(owner: String,userId: String,type: ResourceType,offset: Int=0): PublicFavoritesPage {
        require(UUID.fromString(userId).toString()==userId && type.canFavorite && offset>=0)
        val json=accounts.communityRequest(owner,"GET","/v1/community/users/$userId/favorites?type=${type.resource}&offset=$offset&limit=12")
        return PublicFavoritesProtocol.page(json.toString(),type,offset)
    }
}
