package com.example.marvellobby.presentation

import com.example.marvellobby.data.local.AppPreferences
import com.example.marvellobby.data.model.*
import com.example.marvellobby.data.repository.*

data class Route(val screen: String, val type: ResourceType?=null, val id: Int=0, val title: String="", val section: String?=null,val userId: String?=null) {
    val key: String get() = if(userId!=null)"$screen:$userId"+(if(screen=="socialPeople")":$title" else "") else "$screen:${type?.name}:$id"
}
data class BrowseState(
    val items: List<ComicEntity> = emptyList(), val query: String="", val loading: Boolean=false,
    val error: String?=null, val offset: Int=0, val more: Boolean=true, val offline: Boolean=false,
    val sort: String="date_last_updated:desc", val marvelOnly: Boolean=true, val power: String="",
    val retryAtNanos: Long?=null,
)
data class DetailState(val entity: ComicEntity?=null, val loading: Boolean=false, val error: String?=null, val offline: Boolean=false)
data class RelatedIssuesState(val records: Map<Int,ComicEntity> = emptyMap(),val count: Int=8,
    val loading: Boolean=false,val error: String?=null,val offline: Boolean=false)
data class AiState(
    val context: ComicEntity?=null, val messages: List<ChatMessage> = emptyList(), val sending: Boolean=false,
    val error: String?=null, val sources: List<ComicEntity> = emptyList(),val conversationId: String?=null
)
data class CatalogTextState(val text: String?=null,val loading: Boolean=false,val error: String?=null)
data class AppState(
    val route: Route=Route("splash"), val preferences: AppPreferences=AppPreferences(),
    val user: UserProfile?=null, val library: List<LibraryItem> = emptyList(),
    val pages: Map<String,BrowseState> = emptyMap(), val details: Map<String,DetailState> = emptyMap(),
    val home: BrowseState=BrowseState(), val ai: AiState=AiState(), val authBusy: Boolean=false,
    val formError: String?=null,val authErrors:Map<String,String> = emptyMap(),val favoriteTab: ResourceType=ResourceType.CHARACTER,
    val searchType: ResourceType?=null, val recentSearches: List<String> = emptyList(),
    val comparisonCharacter: ComicEntity?=null,
    val relatedIssues: Map<String,RelatedIssuesState> = emptyMap(),
    val chats: List<ChatSummary> = emptyList(),val chatHistoryLoading: Boolean=false,val chatHistoryError: String?=null,
    val visibleSecrets: Set<String> = emptySet(),val securityUnlocked: Boolean=false,
    val translatedTexts: Map<String,CatalogTextState> = emptyMap(),val descriptionPage: Map<String,Int> = emptyMap(),
    val originalDescriptions: Set<String> = emptySet(),
    val publicProfile: UserProfile?=null,val publicProfileLoading: Boolean=false,val publicProfileError: String?=null,
    val onlineProfileNotice: String?=null,
    val riftStats: Map<String,com.example.marvellobby.rift.data.RiftStats> = emptyMap(),
    val riftErrors: Map<String,String> = emptyMap()
)
