package com.example.marvellobby.data.remote

import com.example.marvellobby.data.model.*
import com.example.marvellobby.data.repository.UserProfile
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.time.Instant
import java.util.UUID

object CommunityProtocol {
    fun uuid(value: String): String {
        require(UUID.fromString(value).toString()==value) { "Invalid community response." };return value
    }
    private fun JsonObject.text(key: String)=get(key)?.takeUnless { it.isJsonNull }?.asString ?: error("Missing field")
    private fun JsonObject.number(key: String)=text(key).toLong().also { require(it>=0) }
    private fun JsonObject.count(key: String)=number(key).also { require(it<=Int.MAX_VALUE) }.toInt()
    private fun profile(json: JsonObject,avatar: (Int?)->String): UserProfile {
        val avatarId=json.get("avatarId")?.takeUnless { it.isJsonNull }?.asInt
        require(avatarId==null || avatarId in listOf(1440,1443,1455,1442,2268,2267,1444,3200))
        return UserProfile(json.text("name"),"",avatar(avatarId),json.text("username"),uuid(json.text("id")),
            json.text("bio"),Instant.parse(json.text("joinedAt")).toEpochMilli())
    }
    private fun parse(text: String)=JsonParser.parseString(text).asJsonObject
    private fun <T> safe(block: ()->T): T=try { block() } catch(_: RuntimeException) { throw IOException("Invalid community response.") }
    fun social(text: String,avatar: (Int?)->String)=safe {
        val json=parse(text)
        SocialProfile(profile(json.getAsJsonObject("profile"),avatar),json.count("followers"),json.count("following"),
            json.get("isFollowing").asBoolean,json.get("followsYou").asBoolean,json.get("isSelf").asBoolean)
    }
    fun people(text: String,avatar: (Int?)->String)=safe {
        val json=parse(text)
        PeoplePage(json.getAsJsonArray("items").map { profile(it.asJsonObject,avatar) },json.get("next")?.takeUnless { it.isJsonNull }?.asString)
    }
    private fun message(json: JsonObject): DirectMessage {
        val body=json.text("text");require(body.isNotBlank() && body.codePointCount(0,body.length)<=2000)
        return DirectMessage(json.number("id").also { require(it>0) },uuid(json.text("conversationId")),uuid(json.text("senderId")),
            uuid(json.text("clientId")),body,Instant.parse(json.text("sentAt")).toEpochMilli())
    }
    fun message(text: String)=safe { message(parse(text)) }
    fun conversation(text: String,avatar: (Int?)->String)=safe {
        val json=parse(text);DirectConversation(uuid(json.text("id")),profile(json.getAsJsonObject("peer"),avatar))
    }
    fun inbox(text: String,avatar: (Int?)->String)=safe {
        val json=parse(text)
        InboxPage(json.getAsJsonArray("items").map { element ->
            val item=element.asJsonObject
            DirectConversation(uuid(item.text("id")),profile(item.getAsJsonObject("peer"),avatar),item.count("unread"),
                item.get("lastMessage")?.takeUnless { it.isJsonNull }?.let { message(it.asJsonObject) })
        },json.get("next")?.takeUnless { it.isJsonNull }?.asInt,json.count("unreadTotal"))
    }
    fun messages(text: String,id: String,avatar: (Int?)->String)=safe {
        val json=parse(text);val peer=profile(json.getAsJsonObject("peer"),avatar)
        val messages=json.getAsJsonArray("items").map { message(it.asJsonObject).also { record -> require(record.conversationId==id) } }
        DirectPage(peer,messages,json.get("hasMore").asBoolean,json.number("peerLastRead"))
    }
    fun event(text: String)=safe {
        require(text.length<=256)
        val json=parse(text);val type=json.text("type")
        require(type in setOf("ready","community","messages","read"))
        CommunityEvent(type,json.get("conversationId")?.takeUnless { it.isJsonNull }?.asString?.let(::uuid))
    }
}
