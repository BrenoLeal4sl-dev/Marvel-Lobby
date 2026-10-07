package com.example.marvellobby.rift.data

import com.example.marvellobby.data.repository.OnlineAccountRepository
import com.example.marvellobby.data.remote.LobbyApiException
import com.example.marvellobby.rift.engine.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

data class RiftSession(val id: String,val seed: Int,val character: String,val version: String,val challengeId: String?)
data class RiftStats(val runs: Int=0,val best: Int=0,val survival: Float=0f,val kills: Int=0,val bosses: Int=0,val globalPosition: Int?=null)
data class RiftRank(val userId: String,val username: String,val name: String,val score: Int,val position: Int)
data class RiftRankPage(val items: List<RiftRank>,val next: Int?,val ownPosition: Int?,val ownScore: Int?)
data class RiftChallenge(val id: String,val challenger: String,val challenged: String,val ownScore: Int?,val peerScore: Int?,val expiresAt: String,val attempted: Boolean=false,val version: String=RiftRules.VERSION)
data class RiftChallengePage(val items: List<RiftChallenge>,val next: Int?)
class RiftRepository(private val dao: RiftDao,private val accounts: OnlineAccountRepository) {
    private val gson=Gson();private val mutex=Mutex()
    suspend fun session(owner: String,clientId: String,challengeId: String?): RiftSession {
        val json=accounts.riftRequest(owner,"POST","/v1/rift/sessions",JSONObject().put("clientId",clientId).apply { challengeId?.let { put("challengeId",it) } })
        val id=json.getString("id");require(UUID.fromString(id).toString()==id)
        require(json.getString("version")==RiftRules.VERSION && json.getString("character")==RiftCharacters.spider.key)
        return RiftSession(id,json.getInt("seed"),json.getString("character"),json.getString("version"),json.optString("challengeId").takeUnless { it=="null"||it.isBlank() })
    }
    suspend fun save(owner: String,id: String,result: RunResult,session: RiftSession?)=withContext(Dispatchers.IO+NonCancellable) {
        dao.insert(StoredRiftRun().apply { this.id=id;this.owner=owner;payload=gson.toJson(result);sessionId=session?.id
            status=if(session!=null)"pending" else "practice";endedAt=System.currentTimeMillis()
            score=result.score;duration=result.duration;kills=result.kills;bosses=result.bosses })
    }
    suspend fun history(owner: String)=withContext(Dispatchers.IO) { dao.history(owner) }
    fun result(row: StoredRiftRun): RunResult=gson.fromJson(row.payload,RunResult::class.java)
    suspend fun sync(owner: String)=withContext(Dispatchers.IO) { mutex.withLock {
        if(!owner.startsWith("remote:"))return@withLock
        for(row in dao.pending(owner)) {
            try {
                val response=accounts.riftRequest(owner,"POST","/v1/rift/sessions/${row.sessionId}/result",JSONObject(row.payload))
                require(response.getBoolean("accepted") && response.getInt("score")==result(row).score)
                dao.status(owner,row.id,"synced")
            } catch(error: LobbyApiException) {
                if(error.status in listOf(400,404,409))dao.status(owner,row.id,"rejected")else throw error
            }
        }
    } }
    suspend fun ranking(owner: String,mode: String,offset: Int): RiftRankPage {
        require(mode in setOf("global","friends","weekly","character") && offset>=0)
        val json=accounts.riftRequest(owner,"GET","/v1/rift/ranking?mode=$mode&offset=$offset")
        val list=json.getJSONArray("items")
        val items=(0 until list.length()).map { val row=list.getJSONObject(it)
            RiftRank(UUID.fromString(row.getString("userId")).toString(),row.getString("username"),row.getString("name"),row.getInt("score"),row.getInt("position")) }
        val own=json.optJSONObject("own")
        return RiftRankPage(items,json.optInt("next").takeUnless { json.isNull("next") },own?.getInt("position"),own?.getInt("score"))
    }
    suspend fun abandon(owner: String,id: String)=accounts.riftRequest(owner,"POST","/v1/rift/sessions/${UUID.fromString(id)}/abandon",JSONObject())
    suspend fun profile(owner: String,userId: String): RiftStats {
        val j=accounts.riftRequest(owner,"GET","/v1/rift/users/${UUID.fromString(userId)}")
        return RiftStats(j.getInt("runs"),j.getInt("best"),j.getDouble("survival").toFloat(),j.getInt("kills"),j.getInt("bosses"),j.optInt("globalPosition").takeUnless { j.isNull("globalPosition") })
    }
    suspend fun challenges(owner: String,offset: Int,id: String?=null): RiftChallengePage {
        val filter=id?.let { "&id=${UUID.fromString(it)}" }.orEmpty()
        val j=accounts.riftRequest(owner,"GET","/v1/rift/challenges?offset=$offset$filter");val array=j.getJSONArray("items")
        return RiftChallengePage((0 until array.length()).map { val r=array.getJSONObject(it)
            RiftChallenge(UUID.fromString(r.getString("id")).toString(),r.getString("challenger"),r.getString("challenged"),r.optInt("own_score").takeUnless { r.isNull("own_score") },r.optInt("peer_score").takeUnless { r.isNull("peer_score") },r.getString("expires_at"),r.optBoolean("attempted"),r.optString("game_version",RiftRules.VERSION)) },j.optInt("next").takeUnless { j.isNull("next") })
    }
    suspend fun challenge(owner: String,target: String,nonce: String)=accounts.riftRequest(owner,"POST","/v1/rift/challenges",JSONObject().put("userId",UUID.fromString(target).toString()).put("clientId",nonce))
    suspend fun localStats(owner: String): RiftStats=withContext(Dispatchers.IO) {
        val total=dao.totals(owner);RiftStats(total.runs,total.best,total.survival,total.kills,total.bosses)
    }
}
