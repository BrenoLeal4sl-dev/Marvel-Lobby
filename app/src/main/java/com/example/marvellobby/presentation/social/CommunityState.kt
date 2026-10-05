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
    val sending: Boolean=false,val pending: PendingDirectMessage?=null,val error: String?=null,val sendError: String?=null,
    val syncedThrough: Long=0)

/** A send ACK can arrive before a preceding incoming message. Only fetched pages advance the cursor. */
fun DirectChatState.acknowledge(message: DirectMessage)=copy(
    messages=mergeDirectMessages(messages,listOf(message)),sending=false,pending=null,sendError=null)

fun DirectChatState.receivePage(page: DirectPage,advanceCursor: Boolean=true)=copy(
    peer=page.peer,messages=mergeDirectMessages(messages,page.items),
    syncedThrough=if(advanceCursor)maxOf(syncedThrough,page.items.lastOrNull()?.id ?: 0) else syncedThrough,
    peerLastRead=maxOf(peerLastRead,page.peerLastRead),error=null)
data class CommunityState(val profiles: Map<String,SocialProfile> = emptyMap(),val profileLoading: Set<String> = emptySet(),
    val profileErrors: Map<String,String> = emptyMap(),val followBusy: Set<String> = emptySet(),
    val people: Map<String,PeopleState> = emptyMap(),val inbox: InboxState=InboxState(),
    val chats: Map<String,DirectChatState> = emptyMap(),val connected: Boolean=false,val connecting: Boolean=false,
    val opening: Boolean=false,val notice: String?=null,val requiresSignIn: Boolean=false)

/** Pause online work without discarding messages, pending send nonces, people or drafts. */
fun CommunityState.sessionRequired(required: Boolean)=copy(
    requiresSignIn=required,connected=false,connecting=false,opening=false,notice=null,
    profileLoading=emptySet(),followBusy=emptySet(),profileErrors=if(required)profileErrors else emptyMap(),
    people=people.mapValues { (_,page)->page.copy(loading=false,error=if(required)page.error else null) },
    inbox=inbox.copy(loading=false,error=if(required)inbox.error else null),
    chats=chats.mapValues { (_,chat)->chat.copy(loading=false,sending=false,
        error=if(required)chat.error else null,sendError=if(required)chat.sendError else
            if(chat.pending!=null)"The previous send was interrupted. Retry to confirm delivery." else null) })
