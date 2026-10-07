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
    private var cloudLoop: Job?=null
    private val jobs=mutableMapOf<String,Job>()
    private val readThrough=mutableMapOf<String,Long>()
    val drafts=mutableMapOf<String,String>()
    val userId get()=owner.removePrefix("remote:")
    val online get()=owner.startsWith("remote:")
    private val canRequest get()=online && !state.value.requiresSignIn
    init { viewModelScope.launch { app.library.favoriteChanges.collect { changed -> if(changed==owner)syncFavorites() } } }
    init { viewModelScope.launch {
        combine(app.preferences.flow.map { it.session },app.onlineAccounts.sessionValidity) { session,validity ->
            session to validity[session]
        }.distinctUntilChanged().collect { (session,valid) ->
            val changed=owner!=session
            val recovering=!changed && state.value.requiresSignIn && valid==true
            if(changed || valid==false || recovering) {
                live?.cancel();live=null;cloudLoop?.cancel();cloudLoop=null;jobs.values.forEach { it.cancel() };jobs.clear()
                if(changed) { drafts.clear();readThrough.clear();owner=session;mutable.value=CommunityState() }
                mutable.update { it.sessionRequired(valid==false) }
            }
            if(changed || recovering) { enter();startLive();startCloud();if(canRequest)syncFavorites();if(recovering)loadInbox() }
        }
    } }
    private fun error(e: Exception)=when(e) {
        is LobbyApiException -> e.message.orEmpty()
        else -> "Community connection unavailable. Your messages remain saved on the server."
    }
    private fun favoriteError(e: Exception)=if(e is LobbyApiException)e.message.orEmpty() else "Could not update favorites. Try again when connected."
    fun foreground(value: Boolean) { foreground=value;if(value){startLive();startCloud();enter();if(canRequest)syncFavorites()}else{live?.cancel();live=null;cloudLoop?.cancel();cloudLoop=null;mutable.update { it.copy(connected=false,connecting=false) }} }
    private fun startCloud() {
        if(!foreground || !canRequest || cloudLoop?.isActive==true)return
        cloudLoop=viewModelScope.launch { while(isActive && foreground && canRequest) { syncCloud();delay(60_000) } }
    }
    fun syncCloud(choice: Boolean?=null) {
        if(!canRequest)return
        if(choice!=null)jobs["cloud"]?.cancel() else if(jobs["cloud"]?.isActive==true)return
        val forOwner=owner;mutable.update { it.copy(cloud=it.cloud.copy(busy=true,error=null)) }
        jobs["cloud"]=viewModelScope.launch {
            try {
                if(choice!=null)app.cloud.choose(forOwner,choice)
                val enabled=choice ?: app.cloud.enabled(forOwner)
                val title=if(app.preferences.flow.first().language=="pt")"Conversa preservada" else "Recovered conversation"
                val recovered=if(enabled)app.cloud.sync(forOwner,title)>0 else false
                val pending=app.cloud.pending(forOwner)
                if(owner==forOwner)mutable.update { it.copy(cloud=it.cloud.copy(enabled=enabled,busy=false,pending=pending,
                    lastSync=if(enabled)System.currentTimeMillis() else it.cloud.lastSync,recovered=it.cloud.recovered||recovered)) }
            } catch(c: CancellationException) { throw c }
            catch(e: Exception) {
                val pending=app.cloud.pending(forOwner)
                if(owner==forOwner)mutable.update { it.copy(cloud=it.cloud.copy(busy=false,pending=pending,error=
                    if(e is LobbyApiException)e.message.orEmpty() else "Could not synchronize. Your records remain saved on this device.")) }
            }
        }
    }
    fun route(value: Route) { if(route==value)return;route=value;enter() }
    private fun enter() {
        if(!canRequest)return
        when(route.screen) {
            "community","socialPeople","shareContent","shareRift" -> if(state.value.people[route.key]?.loading!=true)loadPeople()
            "activity" -> { loadActivity();activityPrivacy() }
            "notifications" -> loadNotifications()
            "socialPrivacy" -> activityPrivacy()
            "cloudSync" -> syncCloud()
            "inbox" -> if(!state.value.inbox.loaded)loadInbox()
            "publicProfile" -> route.userId?.let { loadProfile(it);loadFavorites(it) }
            "profile" -> { loadProfile(userId);syncFavorites() }
            "directChat" -> route.userId?.let { id -> if(state.value.chats[id]?.loaded!=true)loadChat(id) else syncChat(id) }
        }
    }
    private fun startLive() {
        if(!foreground||!canRequest||live?.isActive==true)return
        val forOwner=owner
        live=viewModelScope.launch {
            var wait=2_000L
            while(isActive && foreground && owner==forOwner && canRequest) {
                mutable.update { it.copy(connecting=true) }
                try {
                    app.community.events(forOwner).collect { event ->
                        if(owner!=forOwner)return@collect
                        if(event.type in listOf("ready","community","messages","read")) {
                            loadNotifications()
                            if(route.screen=="activity")loadActivity()
                        }
                        if(event.type=="ready") {
                            wait=2_000L;mutable.update { it.copy(connected=true,connecting=false) }
                            loadInbox();loadProfile(userId)
                            syncFavorites()
                            if(route.screen in listOf("community","socialPeople","shareContent","shareRift") && state.value.people[route.key]?.loading!=true)loadPeople()
                            if(route.screen=="directChat")route.userId?.let { syncChat(it) }
                        }
                        if(event.type=="messages" || event.type=="read") {
                            loadInbox()
                            if(route.screen=="directChat" && route.userId==event.conversationId)syncChat(event.conversationId!!)
                        }
                        if(event.type=="community") {
                            loadProfile(userId)
                            if(route.screen in listOf("community","socialPeople") && state.value.people[route.key]?.loading!=true)loadPeople()
                            if(route.screen=="publicProfile")route.userId?.let { loadProfile(it);loadFavorites(it) }
                        }
                    }
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { if(owner==forOwner)mutable.update { it.copy(connected=false,connecting=false) } }
                delay(wait);wait=(wait*2).coerceAtMost(30_000)
            }
        }
    }
    fun loadProfile(id: String) {
        if(!canRequest || jobs["profile:$id"]?.isActive==true)return
        val forOwner=owner
        mutable.update { it.copy(profileLoading=it.profileLoading+id,profileErrors=it.profileErrors-id) }
        jobs["profile:$id"]=viewModelScope.launch {
            try { val profile=app.community.profile(forOwner,id);if(owner==forOwner)mutable.update { it.copy(profiles=it.profiles+(id to profile),profileLoading=it.profileLoading-id) } }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(profileLoading=it.profileLoading-id,profileErrors=it.profileErrors+(id to error(e))) } }
        }
    }
    fun favoriteType(id: String)=state.value.favoriteTypes[id] ?: ResourceType.CHARACTER
    fun selectFavoriteType(id: String,type: ResourceType) {
        require(type.canFavorite)
        mutable.update { it.copy(favoriteTypes=it.favoriteTypes+(id to type)) }
        loadFavorites(id)
    }
    fun loadFavorites(id: String,more: Boolean=false) {
        if(!canRequest)return
        val type=favoriteType(id);val key="$id:${type.name}"
        val old=state.value.publicFavorites[key] ?: PublicFavoritesState()
        if(jobs["favorites:$key"]?.isActive==true || (more && old.next==null))return
        val forOwner=owner
        mutable.update { it.copy(publicFavorites=it.publicFavorites+(key to old.copy(loading=true,error=null))) }
        jobs["favorites:$key"]=viewModelScope.launch {
            try {
                val page=app.publicFavorites.list(forOwner,id,type,if(more)old.next!! else 0)
                if(owner==forOwner)mutable.update { it.receiveFavorites(id,type,page,more) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(publicFavorites=it.publicFavorites+(key to old.copy(loading=false,error=favoriteError(e)))) } }
        }
    }
    fun shareFavorites(enabled: Boolean) {
        if(!canRequest || state.value.sharing.busy)return
        val forOwner=owner
        mutable.update { it.copy(sharing=it.sharing.copy(busy=true,error=null)) }
        jobs["favorite-sync"]=viewModelScope.launch {
            try {
                app.publicFavorites.chooseSharing(forOwner,enabled)
                val result=app.publicFavorites.sync(forOwner)
                if(owner==forOwner) {
                    mutable.update { it.copy(sharing=FavoriteSharingState(result)) }
                    if(route.screen=="publicProfile")route.userId?.let { loadFavorites(it) }
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(sharing=it.sharing.copy(busy=false,error=favoriteError(e))) } }
        }
    }
    fun syncFavorites() {
        if(!canRequest)return
        if(jobs["favorite-sync"]?.isActive==true) {
            if(jobs["favorite-resync"]?.isActive!=true)jobs["favorite-resync"]=viewModelScope.launch {
                jobs["favorite-sync"]?.join();syncFavorites()
            }
            return
        }
        val forOwner=owner;mutable.update { it.copy(sharing=it.sharing.copy(busy=true,error=null)) }
        jobs["favorite-sync"]=viewModelScope.launch {
            try {
                val result=app.publicFavorites.sync(forOwner)
                if(owner==forOwner)mutable.update { it.copy(sharing=FavoriteSharingState(result)) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(sharing=it.sharing.copy(busy=false,error=favoriteError(e))) } }
        }
    }
    fun follow(id: String) {
        val profile=state.value.profiles[id] ?: return
        if(!canRequest || id in state.value.followBusy||profile.isSelf)return
        val forOwner=owner;mutable.update { it.copy(followBusy=it.followBusy+id,notice=null) }
        jobs["follow:$id"]=viewModelScope.launch {
            try { val updated=app.community.follow(forOwner,id,!profile.isFollowing)
                if(owner==forOwner){mutable.update { it.copy(profiles=it.profiles+(id to updated),followBusy=it.followBusy-id) };loadProfile(userId)}
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(followBusy=it.followBusy-id,notice=error(e)) } }
        }
    }
    fun searchPeople(query: String) {
        if(!canRequest)return
        val key=route.key;val old=state.value.people[key] ?: PeopleState()
        if(old.query==query)return
        jobs["people:$key"]?.cancel()
        mutable.update { it.copy(people=it.people+(key to PeopleState(query=query,loading=true))) }
        loadPeople(debounce=true)
    }
    fun refreshPeople() {
        if(!canRequest || state.value.people[route.key]?.loading==true)return
        loadPeople()
        loadInbox()
        loadProfile(userId)
        loadNotifications()
    }
    fun prepareShare(entity: ComicEntity) {
        if(!canRequest || !entity.type.canFavorite)return
        mutable.update { it.copy(shareContent=SharedContent.from(entity),notice=null) }
        navigation.trySend(Route("shareContent"))
    }
    fun prepareRift(record: RiftShare) {
        if(!canRequest)return
        mutable.update { it.copy(shareRift=record,notice=null) };navigation.trySend(Route("shareRift"))
    }
    fun loadActivity(more: Boolean=false) {
        if(!canRequest||jobs["activity"]?.isActive==true)return
        val prior=state.value.activity;if(more && prior.next==null)return
        val forOwner=owner;mutable.update { it.copy(activity=it.activity.copy(loading=true,error=null)) }
        jobs["activity"]=viewModelScope.launch {
            try { val page=app.community.activity(forOwner,if(more)prior.next!! else 0)
                if(owner==forOwner)mutable.update { it.copy(activity=ActivityState(
                    ((if(more)prior.items else emptyList())+page.items).distinctBy { a->"${a.actor.id}:${a.content.type}:${a.content.id}" },page.next,loaded=true)) }
            } catch(c: CancellationException) { throw c }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(activity=it.activity.copy(loading=false,error=error(e))) } }
        }
    }
    fun activityPrivacy(enabled: Boolean?=null) {
        if(!canRequest||jobs["activity-privacy"]?.isActive==true)return
        val forOwner=owner;mutable.update { it.copy(activitySharing=it.activitySharing.copy(busy=true,error=null)) }
        jobs["activity-privacy"]=viewModelScope.launch {
            try { val choice=app.community.activitySharing(forOwner,enabled)
                if(owner==forOwner)mutable.update { it.copy(activitySharing=FavoriteSharingState(choice)) }
            } catch(c: CancellationException) { throw c }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(activitySharing=it.activitySharing.copy(busy=false,error=error(e))) } }
        }
    }
    fun loadNotifications(more: Boolean=false) {
        if(!canRequest||jobs["notifications"]?.isActive==true)return
        val prior=state.value.notifications;if(more && prior.next==null)return
        val forOwner=owner;mutable.update { it.copy(notifications=it.notifications.copy(loading=true,error=null)) }
        jobs["notifications"]=viewModelScope.launch {
            try { val page=app.community.notifications(forOwner,if(more)prior.next!! else 0)
                if(owner==forOwner)mutable.update { it.copy(notifications=NotificationsState(
                    ((if(more)prior.items else emptyList())+page.items).distinctBy { n->n.key },page.next,loaded=true,unread=page.unread)) }
            } catch(c: CancellationException) { throw c }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(notifications=it.notifications.copy(loading=false,error=error(e))) } }
        }
    }
    fun readNotifications(items: List<CommunityNotification>) {
        if(!canRequest)return
        val forOwner=owner
        jobs["notification-read"]=viewModelScope.launch {
            try { app.community.readNotifications(forOwner,items.filter { !it.read }.take(100).map { it.key });if(owner==forOwner)loadNotifications() }
            catch(c: CancellationException) { throw c }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(notifications=it.notifications.copy(error=error(e))) } }
        }
    }
    fun loadPeople(more: Boolean=false,debounce: Boolean=false) {
        if(!canRequest)return
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
        if(!canRequest)return
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
    fun openConversation(id: String,shared: SharedContent?=null,rift: RiftShare?=null) {
        if(!canRequest||state.value.opening)return
        val forOwner=owner;mutable.update { it.copy(opening=true,notice=null) }
        jobs["open"]=viewModelScope.launch {
            try { val conversation=app.community.open(forOwner,id)
                if(owner==forOwner && (shared!=null||rift!=null) && state.value.chats[conversation.id]?.pending!=null) {
                    mutable.update { it.copy(opening=false,notice="This conversation has a pending send. Open Messages and retry it before sharing another record.") }
                    return@launch
                }
                if(owner==forOwner){mutable.update { it.copy(opening=false,chats=it.chats+(conversation.id to (it.chats[conversation.id] ?: DirectChatState(peer=conversation.peer)))) }
                    navigation.send(Route("directChat",title=conversation.peer.name,userId=conversation.id))
                    if(shared!=null) {
                        chat(conversation.id) { it.copy(pending=PendingDirectMessage(shared.name,UUID.randomUUID().toString(),shared)) }
                        mutable.update { it.copy(shareContent=null) };send(conversation.id,retry=true)
                    }
                    if(rift!=null) {
                        chat(conversation.id) { it.copy(pending=PendingDirectMessage("Rift Arena",UUID.randomUUID().toString(),rift=rift)) }
                        mutable.update { it.copy(shareRift=null) };send(conversation.id,retry=true)
                    }
                }
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
        if(!canRequest||jobs["chat:$id"]?.isActive==true)return
        val old=state.value.chats[id] ?: DirectChatState();if(older&&(!old.hasOlder||old.messages.isEmpty()))return
        val forOwner=owner;chat(id) { it.copy(loading=true,error=null) }
        jobs["chat:$id"]=viewModelScope.launch {
            try { val page=app.community.messages(forOwner,id,if(older)old.messages.first().id else null)
                if(owner==forOwner)chat(id) { it.receivePage(page,advanceCursor=!older).copy(loading=false,
                    loaded=true,hasOlder=page.hasMore) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)chat(id) { it.copy(loading=false,error=error(e)) } }
        }
    }
    fun syncChat(id: String) {
        if(!canRequest)return
        if(jobs["chat:$id"]?.isActive==true){jobs["resync:$id"]?.cancel();jobs["resync:$id"]=viewModelScope.launch { jobs["chat:$id"]?.join();syncChat(id) };return}
        val old=state.value.chats[id] ?: DirectChatState();if(!old.loaded){loadChat(id);return}
        val forOwner=owner
        jobs["chat:$id"]=viewModelScope.launch {
            try {
                var after=old.syncedThrough
                do {
                    val page=app.community.messages(forOwner,id,after=after)
                    if(owner!=forOwner)return@launch
                    chat(id) { it.receivePage(page) }
                    val next=page.items.lastOrNull()?.id ?: after
                    if(!page.hasMore||next<=after)break
                    after=next;ensureActive()
                } while(true)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)chat(id) { it.copy(error=error(e)) } }
        }
    }
    fun send(id: String,retry: Boolean=false) {
        val old=state.value.chats[id] ?: return;if(old.sending||!canRequest)return
        val pending=if(retry)old.pending else PendingDirectMessage(drafts[id].orEmpty().trim(),UUID.randomUUID().toString())
        if(pending==null||pending.text.isBlank())return
        if(!retry && old.pending!=null)return // Resolve an ambiguous failed send before creating another nonce.
        val forOwner=owner;chat(id) { it.copy(sending=true,pending=pending,sendError=null) }
        jobs["send:$id"]=viewModelScope.launch {
            try { val sent=app.community.send(forOwner,id,pending.text,pending.clientId,pending.shared,pending.rift)
                if(owner==forOwner) {
                    if(pending.shared==null && pending.rift==null && drafts[id]?.trim()==pending.text)drafts[id]=""
                    chat(id) { it.acknowledge(sent) }
                    loadInbox();syncChat(id)
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)chat(id) { it.copy(sending=false,sendError=error(e)) } }
        }
    }
    fun markRead(id: String) {
        if(!canRequest||!foreground||route.screen!="directChat"||route.userId!=id)return
        val last=state.value.chats[id]?.syncedThrough?.takeIf { it>0 } ?: return
        if(last<=(readThrough[id] ?: 0L)||jobs["read:$id"]?.isActive==true)return
        val forOwner=owner
        jobs["read:$id"]=viewModelScope.launch {
            try { app.community.read(forOwner,id,last);if(owner==forOwner){readThrough[id]=last;loadInbox()} }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { /* Receipt is retried after the next visible update, never a message resend. */ }
        }
    }
}
