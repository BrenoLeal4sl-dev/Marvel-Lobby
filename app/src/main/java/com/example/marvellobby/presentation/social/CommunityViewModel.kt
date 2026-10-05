package com.example.marvellobby.presentation.social

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.marvellobby.MarvelApplication
import com.example.marvellobby.data.model.*
import com.example.marvellobby.data.remote.LobbyApiException
import com.example.marvellobby.presentation.Route
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID

class CommunityViewModel(application: Application): AndroidViewModel(application) {
    private val app=application as MarvelApplication
    private val mutable=MutableStateFlow(CommunityState())
    val state=mutable.asStateFlow()
    val navigation=Channel<Route>(Channel.BUFFERED)
    private var owner=""
    private var route=Route("splash")
    private var foreground=false
    private var live: Job?=null
    private val jobs=mutableMapOf<String,Job>()
    private val readThrough=mutableMapOf<String,Long>()
    val drafts=mutableMapOf<String,String>()
    val userId get()=owner.removePrefix("remote:")
    val online get()=owner.startsWith("remote:")
    init { viewModelScope.launch {
        app.preferences.flow.map { it.session }.distinctUntilChanged().collect { session ->
            live?.cancel();live=null;jobs.values.forEach { it.cancel() };jobs.clear();drafts.clear();readThrough.clear()
            owner=session;mutable.value=CommunityState()
            enter();startLive()
        }
    } }
    private fun error(e: Exception)=when(e) {
        is LobbyApiException -> e.message.orEmpty()
        else -> "Community connection unavailable. Your messages remain saved on the server."
    }
    fun foreground(value: Boolean) { foreground=value;if(value){startLive();enter()}else{live?.cancel();live=null;mutable.update { it.copy(connected=false,connecting=false) }} }
    fun route(value: Route) { if(route==value)return;route=value;enter() }
    private fun enter() {
        if(!online)return
        when(route.screen) {
            "community","socialPeople" -> if(state.value.people[route.key]?.loaded!=true)loadPeople()
            "inbox" -> if(!state.value.inbox.loaded)loadInbox()
            "publicProfile" -> route.userId?.let { loadProfile(it) }
            "profile" -> loadProfile(userId)
            "directChat" -> route.userId?.let { id -> if(state.value.chats[id]?.loaded!=true)loadChat(id) else syncChat(id) }
        }
    }
    private fun startLive() {
        if(!foreground||!online||live?.isActive==true)return
        val forOwner=owner
        live=viewModelScope.launch {
            var wait=2_000L
            while(isActive && foreground && owner==forOwner) {
                mutable.update { it.copy(connecting=true) }
                try {
                    app.community.events(forOwner).collect { event ->
                        if(owner!=forOwner)return@collect
                        if(event.type=="ready") {
                            wait=2_000L;mutable.update { it.copy(connected=true,connecting=false) }
                            loadInbox();loadProfile(userId)
                            if(route.screen=="directChat")route.userId?.let { syncChat(it) }
                        }
                        if(event.type=="messages" || event.type=="read") {
                            loadInbox()
                            if(route.screen=="directChat" && route.userId==event.conversationId)syncChat(event.conversationId!!)
                        }
                        if(event.type=="community") {
                            loadProfile(userId)
                            if(route.screen=="publicProfile")route.userId?.let { loadProfile(it) }
                        }
                    }
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { if(owner==forOwner)mutable.update { it.copy(connected=false,connecting=false) } }
                delay(wait);wait=(wait*2).coerceAtMost(30_000)
            }
        }
    }
    fun loadProfile(id: String) {
        if(!online || jobs["profile:$id"]?.isActive==true)return
        val forOwner=owner
        mutable.update { it.copy(profileLoading=it.profileLoading+id,profileErrors=it.profileErrors-id) }
        jobs["profile:$id"]=viewModelScope.launch {
            try { val profile=app.community.profile(forOwner,id);if(owner==forOwner)mutable.update { it.copy(profiles=it.profiles+(id to profile),profileLoading=it.profileLoading-id) } }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(profileLoading=it.profileLoading-id,profileErrors=it.profileErrors+(id to error(e))) } }
        }
    }
    fun follow(id: String) {
        val profile=state.value.profiles[id] ?: return
        if(id in state.value.followBusy||profile.isSelf)return
        val forOwner=owner;mutable.update { it.copy(followBusy=it.followBusy+id,notice=null) }
        jobs["follow:$id"]=viewModelScope.launch {
            try { val updated=app.community.follow(forOwner,id,!profile.isFollowing)
                if(owner==forOwner){mutable.update { it.copy(profiles=it.profiles+(id to updated),followBusy=it.followBusy-id) };loadProfile(userId)}
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(followBusy=it.followBusy-id,notice=error(e)) } }
        }
    }
    fun searchPeople(query: String) {
        val key=route.key;val old=state.value.people[key] ?: PeopleState()
        if(old.query==query)return
        jobs["people:$key"]?.cancel()
        mutable.update { it.copy(people=it.people+(key to PeopleState(query=query,loading=true))) }
        loadPeople(debounce=true)
    }
    fun loadPeople(more: Boolean=false,debounce: Boolean=false) {
        if(!online)return
        val target=route;val key=target.key;val old=state.value.people[key] ?: PeopleState()
        if(more && (old.loading||old.next==null))return
        val forOwner=owner;jobs["people:$key"]?.cancel()
        mutable.update { it.copy(people=it.people+(key to old.copy(loading=true,error=null))) }
        jobs["people:$key"]=viewModelScope.launch {
            try {
                if(debounce)delay(400)
                val page=app.community.people(forOwner,old.query,if(more)old.next else null,
                    if(target.screen=="socialPeople")target.userId else null,if(target.title=="following")"following" else "followers")
                if(owner==forOwner)mutable.update { it.copy(people=it.people+(key to old.copy(
                    items=((if(more)old.items else emptyList())+page.items).distinctBy { p->p.id },next=page.next,loading=false,loaded=true,error=null))) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(people=it.people+(key to old.copy(loading=false,error=error(e)))) } }
        }
    }
    fun loadInbox(more: Boolean=false) {
        if(!online)return
        if(jobs["inbox"]?.isActive==true) {
            if(!more && jobs["inbox-resync"]?.isActive!=true)jobs["inbox-resync"]=viewModelScope.launch {
                jobs["inbox"]?.join();loadInbox()
            }
            return
        }
        val old=state.value.inbox;if(more && old.next==null)return
        val forOwner=owner;mutable.update { it.copy(inbox=it.inbox.copy(loading=true,error=null)) }
        jobs["inbox"]=viewModelScope.launch {
            try { val page=app.community.inbox(forOwner,if(more)old.next!! else 0)
                if(owner==forOwner)mutable.update { current -> current.copy(inbox=InboxState(
                    items=if(more)(old.items+page.items).distinctBy { it.id } else page.items+(old.items.filter { oldItem->page.items.none { it.id==oldItem.id } }),
                    next=if(more||old.items.size<=page.items.size)page.next else old.next,loading=false,loaded=true,unreadTotal=page.unreadTotal)) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(inbox=it.inbox.copy(loading=false,error=error(e))) } }
        }
    }
    fun openConversation(id: String) {
        if(!online||state.value.opening)return
        val forOwner=owner;mutable.update { it.copy(opening=true,notice=null) }
        jobs["open"]=viewModelScope.launch {
            try { val conversation=app.community.open(forOwner,id)
                if(owner==forOwner){mutable.update { it.copy(opening=false,chats=it.chats+(conversation.id to (it.chats[conversation.id] ?: DirectChatState(peer=conversation.peer)))) }
                    navigation.send(Route("directChat",title=conversation.peer.name,userId=conversation.id))}
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(opening=false,notice=error(e)) } }
        }
    }
    fun openThread(conversation: DirectConversation) {
        mutable.update { it.copy(chats=it.chats+(conversation.id to (it.chats[conversation.id] ?: DirectChatState(peer=conversation.peer)))) }
        navigation.trySend(Route("directChat",title=conversation.peer.name,userId=conversation.id))
    }
    private fun chat(id: String,change: (DirectChatState)->DirectChatState) { mutable.update { it.copy(chats=it.chats+(id to change(it.chats[id] ?: DirectChatState()))) } }
    fun loadChat(id: String,older: Boolean=false) {
        if(!online||jobs["chat:$id"]?.isActive==true)return
        val old=state.value.chats[id] ?: DirectChatState();if(older&&(!old.hasOlder||old.messages.isEmpty()))return
        val forOwner=owner;chat(id) { it.copy(loading=true,error=null) }
        jobs["chat:$id"]=viewModelScope.launch {
            try { val page=app.community.messages(forOwner,id,if(older)old.messages.first().id else null)
                if(owner==forOwner)chat(id) { it.copy(peer=page.peer,messages=mergeDirectMessages(it.messages,page.items),loading=false,
                    loaded=true,hasOlder=page.hasMore,peerLastRead=page.peerLastRead,error=null) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)chat(id) { it.copy(loading=false,error=error(e)) } }
        }
    }
    fun syncChat(id: String) {
        if(!online)return
        if(jobs["chat:$id"]?.isActive==true){jobs["resync:$id"]?.cancel();jobs["resync:$id"]=viewModelScope.launch { jobs["chat:$id"]?.join();syncChat(id) };return}
        val old=state.value.chats[id] ?: DirectChatState();if(!old.loaded){loadChat(id);return}
        val forOwner=owner
        jobs["chat:$id"]=viewModelScope.launch {
            try {
                var after=old.messages.lastOrNull()?.id ?: 0L
                do {
                    val page=app.community.messages(forOwner,id,after=after)
                    if(owner!=forOwner)return@launch
                    chat(id) { it.copy(peer=page.peer,messages=mergeDirectMessages(it.messages,page.items),peerLastRead=page.peerLastRead,error=null) }
                    val next=page.items.lastOrNull()?.id ?: after
                    if(!page.hasMore||next<=after)break
                    after=next;ensureActive()
                } while(true)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)chat(id) { it.copy(error=error(e)) } }
        }
    }
    fun send(id: String,retry: Boolean=false) {
        val old=state.value.chats[id] ?: return;if(old.sending||!online)return
        val pending=if(retry)old.pending else PendingDirectMessage(drafts[id].orEmpty().trim(),UUID.randomUUID().toString())
        if(pending==null||pending.text.isBlank())return
        if(!retry && old.pending!=null)return // Resolve an ambiguous failed send before creating another nonce.
        val forOwner=owner;chat(id) { it.copy(sending=true,pending=pending,sendError=null) }
        jobs["send:$id"]=viewModelScope.launch {
            try { val sent=app.community.send(forOwner,id,pending.text,pending.clientId)
                if(owner==forOwner) {
                    if(drafts[id]?.trim()==pending.text)drafts[id]=""
                    chat(id) { it.copy(messages=mergeDirectMessages(it.messages,listOf(sent)),sending=false,pending=null,sendError=null) }
                    loadInbox();syncChat(id)
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)chat(id) { it.copy(sending=false,sendError=error(e)) } }
        }
    }
    fun markRead(id: String) {
        if(!online||!foreground||route.screen!="directChat"||route.userId!=id)return
        val last=state.value.chats[id]?.messages?.lastOrNull()?.id ?: return
        if(last<=(readThrough[id] ?: 0L)||jobs["read:$id"]?.isActive==true)return
        val forOwner=owner
        jobs["read:$id"]=viewModelScope.launch {
            try { app.community.read(forOwner,id,last);if(owner==forOwner){readThrough[id]=last;loadInbox()} }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { /* Receipt is retried after the next visible update, never a message resend. */ }
        }
    }
}
