package com.example.marvellobby.data.remote

import com.example.marvellobby.data.repository.UserProfile
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.UUID

class LobbyApiException(val code: String,val status: Int,message: String): IOException(message)
data class SessionSecrets(val origin: String,val userId: String,val accessToken: String,val refreshToken: String,val accessExpiresAt: Long)
data class LobbySession(val user: UserProfile,val secrets: SessionSecrets)

object LobbyProtocol {
    fun profile(json: JSONObject,avatar: (Int?)->String): UserProfile {
        val id=json.getString("id")
        require(UUID.fromString(id).toString()==id) { "Invalid online profile." }
        val avatarId=if(json.isNull("avatarId"))null else json.getInt("avatarId")
        return UserProfile(json.getString("name"),json.optString("email"),avatar(avatarId),json.getString("username"),id,
            json.optString("bio"),Instant.parse(json.getString("joinedAt")).toEpochMilli())
    }
    fun session(json: JSONObject,origin: String,avatar: (Int?)->String): LobbySession {
        val user=profile(json.getJSONObject("user"),avatar)
        val access=json.getString("accessToken")
        val refresh=json.getString("refreshToken")
        require(access.matches(Regex("[A-Za-z0-9_-]{43}")) && refresh.matches(Regex("[A-Za-z0-9_-]{43}"))) { "Invalid online session." }
        return LobbySession(user,SessionSecrets(origin,user.id!!,access,refresh,Instant.parse(json.getString("accessExpiresAt")).toEpochMilli()))
    }
    fun failure(status: Int,body: String): LobbyApiException {
        val code=runCatching { JSONObject(body).getJSONObject("error").getString("code") }.getOrDefault("SERVICE_UNAVAILABLE")
        // Fixed messages; never display arbitrary server/proxy HTML or private connection details.
        val message=when(code) {
            "USERNAME_TAKEN" -> "That username is already in use."
            "EMAIL_TAKEN" -> "An account with this email already exists."
            "INVALID_CREDENTIALS" -> "Email or password is incorrect."
            "CURRENT_PASSWORD_INCORRECT" -> "Current password is incorrect."
            "SESSION_EXPIRED" -> "Sign in again to continue."
            "INVALID_INPUT" -> "Check your input."
            "USER_NOT_FOUND" -> "This profile is unavailable."
            "RATE_LIMITED" -> "Too many attempts. Please try again later."
            "COMMUNITY_NOT_READY" -> "Community is being updated. Please try again shortly."
            "CONVERSATION_NOT_FOUND" -> "This conversation is unavailable."
            "MESSAGE_CONFLICT" -> "This message could not be resent. Check the conversation before sending it again."
            else -> "The online service is unavailable. Please try again."
        }
        return LobbyApiException(code,status,message)
    }
}
