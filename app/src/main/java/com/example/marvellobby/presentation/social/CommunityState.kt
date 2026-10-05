package com.example.marvellobby.presentation.social

import com.example.marvellobby.data.model.*
import com.example.marvellobby.data.repository.UserProfile

data class PeopleState(val items: List<UserProfile> = emptyList(),val query: String="",val next: String?=null,
    val loading: Boolean=false,val loaded: Boolean=false,val error: String?=null)
data class InboxState(val items: List<DirectConversation> = emptyList(),val next: Int?=null,
    val loading: Boolean=false,val loaded: Boolean=false,val error: String?=null,val unreadTotal: Int=0)
data class PendingDirectMessage(val text: String,val clientId: String)
data class DirectChatState(val peer: UserProfile?=null,val messages: List<DirectMessage> = emptyList(),
    val loading: Boolean=false,val loaded: Boolean=false,val hasOlder: Boolean=false,val peerLastRead: Long=0,
    val sending: Boolean=false,val pending: PendingDirectMessage?=null,val error: String?=null,val sendError: String?=null)
data class CommunityState(val profiles: Map<String,SocialProfile> = emptyMap(),val profileLoading: Set<String> = emptySet(),
    val profileErrors: Map<String,String> = emptyMap(),val followBusy: Set<String> = emptySet(),
    val people: Map<String,PeopleState> = emptyMap(),val inbox: InboxState=InboxState(),
    val chats: Map<String,DirectChatState> = emptyMap(),val connected: Boolean=false,val connecting: Boolean=false,
    val opening: Boolean=false,val notice: String?=null)
