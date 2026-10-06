package com.example.marvellobby.data.repository

import com.example.marvellobby.data.model.*
import com.example.marvellobby.data.remote.CommunityProtocol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class CommunityRepository(private val accounts: OnlineAccountRepository,private val avatars: AvatarRepository) {
    private val client=OkHttpClient.Builder().readTimeout(0,TimeUnit.MILLISECONDS)
        .connectTimeout(15,TimeUnit.SECONDS).pingInterval(20,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private fun avatar(id: Int?)=avatars.choices.firstOrNull { it.id==id }?.uri.orEmpty()
    private fun query(vararg values: Pair<String,String?>)=values.filter { it.second!=null }.joinToString("&",prefix="?") {
        "${it.first}=${URLEncoder.encode(it.second,"UTF-8")}" }
    suspend fun people(owner: String,text: String,after: String?=null,userId: String?=null,direction: String="followers"): PeoplePage {
        require(direction in setOf("followers","following"))
        val path=if(userId==null)"/v1/community/people" else "/v1/community/users/${CommunityProtocol.uuid(userId)}/$direction"
        return CommunityProtocol.people(accounts.communityRequest(owner,"GET",path+query("query" to text,"after" to after)).toString(),::avatar)
    }
    suspend fun profile(owner: String,id: String)=CommunityProtocol.social(accounts.communityRequest(owner,"GET","/v1/community/users/${CommunityProtocol.uuid(id)}").toString(),::avatar)
    suspend fun follow(owner: String,id: String,add: Boolean)=CommunityProtocol.social(accounts.communityRequest(owner,if(add)"POST" else "DELETE",
        "/v1/community/users/${CommunityProtocol.uuid(id)}/follow",if(add)JSONObject() else null).toString(),::avatar)
    suspend fun open(owner: String,id: String)=CommunityProtocol.conversation(accounts.communityRequest(owner,"POST","/v1/community/conversations",JSONObject().put("userId",CommunityProtocol.uuid(id))).toString(),::avatar)
    suspend fun inbox(owner: String,offset: Int=0)=CommunityProtocol.inbox(accounts.communityRequest(owner,"GET","/v1/community/conversations"+query("offset" to offset.toString())).toString(),::avatar)
    suspend fun messages(owner: String,id: String,before: Long?=null,after: Long?=null): DirectPage {
        val path="/v1/community/conversations/${CommunityProtocol.uuid(id)}/messages"+query("before" to before?.toString(),"after" to after?.toString())
        return CommunityProtocol.messages(accounts.communityRequest(owner,"GET",path).toString(),id,::avatar)
    }
    suspend fun send(owner: String,id: String,text: String,clientId: String,shared: SharedContent?=null): DirectMessage {
        val body=JSONObject().put("text",text).put("clientId",CommunityProtocol.uuid(clientId))
        shared?.let { body.put("shared",JSONObject().put("type",it.type.resource).put("id",it.id).put("name",it.name).put("imageUrl",it.imageUrl ?: JSONObject.NULL)) }
        return CommunityProtocol.message(accounts.communityRequest(owner,"POST","/v1/community/conversations/${CommunityProtocol.uuid(id)}/messages",body).toString())
    }
    suspend fun activity(owner: String,offset: Int=0)=CommunityProtocol.activity(accounts.communityRequest(owner,"GET","/v1/community/activity?offset=$offset").toString(),::avatar)
    suspend fun notifications(owner: String,offset: Int=0)=CommunityProtocol.notifications(accounts.communityRequest(owner,"GET","/v1/community/notifications?offset=$offset").toString(),::avatar)
    suspend fun readNotifications(owner: String,keys: List<String>) { accounts.communityRequest(owner,"POST","/v1/community/notifications/read",JSONObject().put("keys",org.json.JSONArray(keys))) }
    suspend fun activitySharing(owner: String,enabled: Boolean?=null): Boolean=accounts.communityRequest(owner,if(enabled==null)"GET" else "PUT","/v1/community/me/activity-sharing",enabled?.let { JSONObject().put("enabled",it) }).getBoolean("enabled")
    suspend fun read(owner: String,id: String,lastId: Long) {
        require(lastId>=0);accounts.communityRequest(owner,"POST","/v1/community/conversations/${CommunityProtocol.uuid(id)}/read",JSONObject().put("lastId",lastId.toString()))
    }
    fun events(owner: String)=callbackFlow {
        val secrets=accounts.realtimeCredentials(owner)
        val request=Request.Builder().url(secrets.origin.replaceFirst("https://","wss://")+"/v1/community/live")
            .header("Authorization","Bearer ${secrets.accessToken}").build()
        val socket=client.newWebSocket(request,object: WebSocketListener() {
            override fun onMessage(webSocket: WebSocket,text: String) {
                try { trySend(CommunityProtocol.event(text)) } catch(error: IOException) { close(error);webSocket.cancel() }
            }
            override fun onClosing(webSocket: WebSocket,code: Int,reason: String) { webSocket.close(code,null);close(IOException("Live updates unavailable. Reconnecting…")) }
            override fun onClosed(webSocket: WebSocket,code: Int,reason: String) { close(IOException("Live updates unavailable. Reconnecting…")) }
            override fun onFailure(webSocket: WebSocket,t: Throwable,response: Response?) { close(IOException("Live updates unavailable. Reconnecting…"));response?.close() }
        })
        awaitClose { socket.cancel() }
    }
}
