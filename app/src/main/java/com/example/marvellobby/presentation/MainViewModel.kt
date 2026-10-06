package com.example.marvellobby.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.marvellobby.MarvelApplication
import com.example.marvellobby.data.api.ComicVineException
import com.example.marvellobby.data.model.*
import com.example.marvellobby.data.repository.*
import com.example.marvellobby.data.remote.LobbyApiException
import com.example.marvellobby.presentation.auth.StartupDestination
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

class MainViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val app=application as MarvelApplication
    private val mutable=MutableStateFlow(AppState())
    val state=mutable.asStateFlow()
    val messages=Channel<String>(Channel.BUFFERED)
    private val navigator=AppNavigator()
    private val jobs=mutableMapOf<String,Job>()
    private val generations=mutableMapOf<String,Int>()
    private var aiJob: Job?=null
    private var suggestionSource: Pair<ResourceType,String>?=null
    private var initialized=false
    private var owner=""
    private val sessionLock=Mutex()
    private var resumeAfterSignIn: Pair<String,Route>?=null
    private var pendingStartupRestore:Pair<Route?,List<Route>>?=null
    val authTouched=mutableSetOf<String>()
    val onlineAvailable get()=app.onlineAccounts.available
    val drafts=mutableMapOf<String,String>()
    val scrollPositions=mutableMapOf<String,Int>()
    private val gson=Gson()

    init {
        viewModelScope.launch {
            app.preferences.flow.collect { prefs ->
                val user=app.accounts.profile(prefs.session)
                owner=prefs.session
                mutable.update { it.copy(preferences=prefs,user=user) }
                if(!initialized) {
                    initialized=true
                    refreshLibrary()
                    refreshChats()
                    delay(3_000)
                    val restored=saved.get<String>("route")?.let { runCatching { gson.fromJson(it,Route::class.java) }.getOrNull() }
                    val route=StartupDestination.resolve(prefs,user!=null,restored)
                    val history=saved.get<String>("backStack")?.let {
                        runCatching { gson.fromJson(it,Array<Route>::class.java).toList() }.getOrNull()
                    }.orEmpty()
                    if(route.screen=="language")pendingStartupRestore=restored to history
                    if(user!=null && route==restored) {
                        showRoute(navigator.restore(route,history))
                    } else navigate(route,replace=true)
                    if(route.screen=="ai")saved.get<String>("chatId")?.let { openConversation(it,false) }
                }
            }
        }
    }
    private fun error(e: Exception) = when(e) {
        is java.net.UnknownHostException, is java.net.ConnectException -> "You're offline. Open Favorites or Recently Viewed to read saved records."
        is IllegalArgumentException -> e.message ?: "Check your input."
        is IOException -> e.message?.takeUnless { it.contains("api_key") || it.contains("https://") } ?: "Connection unavailable. Please try again."
        else -> "Something went wrong. Please try again."
    }
    fun navigate(destination: Route, replace: Boolean=false, replaceCurrent: Boolean=false) {
        showRoute(navigator.navigate(destination,replace,replaceCurrent))
    }
    private fun showRoute(route: Route) {
        if(state.value.route.screen!=route.screen)authTouched.clear()
        mutable.update { it.copy(route=route,formError=null,authErrors=emptyMap(),
            visibleSecrets=if(it.route.key==route.key)it.visibleSecrets else emptySet(),
            securityUnlocked=it.route.key==route.key && it.securityUnlocked) }
        saved["route"]=gson.toJson(route)
        saved["backStack"]=gson.toJson(navigator.backStack)
        when(route.screen) {
            "home" -> if(state.value.home.items.isEmpty() && !state.value.home.loading) loadHome()
            "catalog" -> if(!state.value.pages.containsKey(route.key)) loadPage(route,reset=true)
            "detail" -> state.value.details[route.key]?.entity?.let { record ->
                viewModelScope.launch { recordViewed(record) }
                if(!state.value.relatedIssues.containsKey(route.key))loadRelatedIssues(route)
                prepareDescription(record)
            } ?: loadDetail(route)
            "favorites","history","profile" -> viewModelScope.launch { refreshLibrary() }
            "chats" -> viewModelScope.launch { refreshChats() }
            "publicProfile" -> loadPublicProfile(route.userId.orEmpty())
        }
        if(route.screen=="profile" && state.value.user?.online==true)refreshOnlineProfile()
    }
    fun tab(screen: String) = navigate(Route(screen),replace=true)
    fun back(): Boolean {
        if(state.value.route.screen=="splash")return true
        if(state.value.route.screen=="language")return false
        when(state.value.route.screen) {
            "editBio" -> drafts.remove("bio:text")
            "editProfile","connectAccount" -> { drafts.keys.filter { it.startsWith("profile:") || it.startsWith("connect:") }.toList().forEach(drafts::remove) }
            "filters" -> { drafts.remove("filter:marvel"); drafts.remove("filter:power") }
        }
        val previous=navigator.back(state.value.user!=null) ?: return false
        showRoute(previous)
        return true
    }
    fun onboarding(register: Boolean) {
        viewModelScope.launch { app.preferences.finishOnboarding(); navigate(Route(if(register) "register" else "login"),replace=true) }
    }
    fun chooseInitialLanguage(value:String) {
        if(state.value.authBusy || value !in listOf("pt","en"))return
        mutable.update { it.copy(authBusy=true,formError=null) }
        viewModelScope.launch {
            try {
                app.preferences.language(value)
                val prefs=app.preferences.flow.first()
                val user=app.accounts.profile(prefs.session)
                val pending=pendingStartupRestore
                val target=StartupDestination.resolve(prefs,user!=null,pending?.first)
                mutable.update { it.copy(preferences=prefs,user=user,authBusy=false) }
                pendingStartupRestore=null
                if(user!=null && pending!=null && target==pending.first)showRoute(navigator.restore(target,pending.second))
                else navigate(target,replace=true)
                if(target.screen=="ai")saved.get<String>("chatId")?.let { openConversation(it,false) }
            } catch(cancelled:CancellationException) { throw cancelled }
            catch(e:Exception) { mutable.update { it.copy(authBusy=false,formError=error(e)) } }
        }
    }
    fun authFieldEdited(key:String,value:String) {
        drafts["auth:$key"]=value
        val affected=if(key=="password")setOf("password","confirm") else setOf(key)
        if(state.value.formError!=null || state.value.authErrors.keys.any { it in affected })
            mutable.update { it.copy(formError=null,authErrors=it.authErrors-affected) }
    }
    fun toggleAuthPassword(key:String) {
        mutable.update { it.copy(visibleSecrets=if(key in it.visibleSecrets)it.visibleSecrets-key else it.visibleSecrets+key) }
    }
    fun reauthenticate() {
        if(state.value.authBusy)return
        val user=state.value.user?.takeIf { it.online } ?: return
        if(state.value.route.screen!="login")resumeAfterSignIn=user.ownerKey to state.value.route
        drafts.keys.filter { it.startsWith("auth:") }.toList().forEach(drafts::remove)
        drafts["auth:email"]=user.email
        navigate(Route("login"),replace=true)
        mutable.update { it.copy(formError="Sign in again to continue.") }
    }
    fun authenticate(register: Boolean,name: String,email: String,password: String,confirmation: String,username: String="") {
        if(state.value.authBusy) return
        val invalid=AuthValidation.validate(register,true,name,email,password,confirmation,username)
        if(invalid.isNotEmpty()) {
            authTouched.addAll(invalid.keys)
            mutable.update { it.copy(authErrors=invalid,formError=invalid.values.first()) };return
        }
        mutable.update { it.copy(authBusy=true,formError=null,authErrors=emptyMap()) }
        jobs["auth"]=viewModelScope.launch {
            try {
                if(register)require(password==confirmation) { "Passwords do not match." }
                require(onlineAvailable) { "The online service is not configured yet." }
                val profile=if(register)app.onlineAccounts.register(name,email,password,username) else app.onlineAccounts.login(email,password)
                sessionLock.withLock {
                    currentCoroutineContext().ensureActive()
                    withContext(NonCancellable) {
                        owner=profile.ownerKey
                        app.preferences.session(owner)
                        mutable.update { it.copy(user=profile,authBusy=false) }
                    }
                }
                drafts.keys.filter { it.startsWith("auth") }.toList().forEach { drafts.remove(it) }
                val destination=resumeAfterSignIn?.takeIf { it.first==profile.ownerKey }?.second ?: Route("home")
                resumeAfterSignIn=null
                refreshLibrary(); refreshChats();navigate(destination,replace=true)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) {
                val message=error(e)
                val field=(e as? LobbyApiException)?.field ?: AuthValidation.fieldForMessage(message)
                field?.let(authTouched::add)
                mutable.update { it.copy(authBusy=false,formError=message,authErrors=if(field==null)emptyMap() else mapOf(field to message)) }
            }
        }
    }
    fun guest() { if(state.value.authBusy)return;viewModelScope.launch {
        resumeAfterSignIn=null
        app.preferences.session("guest"); owner="guest"
        mutable.update { it.copy(user=UserProfile("Guest","guest")) }; refreshLibrary(); refreshChats(); tab("home")
    } }
    fun logout() { viewModelScope.launch {
        val previousOwner=owner
        resumeAfterSignIn=null
        jobs.values.forEach { it.cancel() }; aiJob?.cancel(); suggestionSource=null; drafts.clear(); scrollPositions.clear()
        mutable.update { it.copy(authBusy=true) }
        sessionLock.withLock {
            val retired=if(previousOwner.startsWith("remote:"))app.onlineAccounts.retire(previousOwner) else null
            owner=""; saved["chatId"]=null; app.preferences.session("")
            mutable.update { AppState(route=Route("login"),preferences=it.preferences.copy(session="")) }
            navigate(Route("login"),replace=true)
            retired?.let { secrets -> viewModelScope.launch {
                runCatching { app.onlineAccounts.revoke(secrets) }
            } }
        }
    } }
    fun saveProfile(name: String,avatar: String) {
        if(state.value.authBusy) return
        mutable.update { it.copy(authBusy=true,formError=null) }
        jobs["profile"]=viewModelScope.launch {
            try {
                val current=state.value.user ?: error("Account unavailable.")
                val profile=if(current.online)app.onlineAccounts.update(owner,name,current.username,current.bio,avatar,current.email,"","")
                    else app.accounts.update(owner,name,avatar)
                mutable.update { it.copy(user=profile,authBusy=false) }
                drafts.remove("profile:name"); drafts.remove("profile:avatar")
                if(state.value.route.screen=="editProfile") {
                    if(!back()) navigate(Route("profile"),replace=true)
                }
                messages.send("Profile saved")
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { mutable.update { it.copy(authBusy=false,formError=error(e)) } }
        }
    }
    fun saveBio(value: String) {
        if(state.value.authBusy)return
        val current=state.value.user?.takeUnless { it.email=="guest" } ?: return
        val forOwner=current.ownerKey
        mutable.update { it.copy(authBusy=true,formError=null) }
        jobs["bio"]=viewModelScope.launch {
            try {
                val profile=if(current.online)app.onlineAccounts.bio(forOwner,value) else app.accounts.bio(forOwner,value)
                if(owner!=forOwner)return@launch
                mutable.update { it.copy(user=profile,authBusy=false) };drafts.remove("bio:text")
                if(state.value.route.screen=="editBio")back()
                messages.send("Biography saved")
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(authBusy=false,formError=error(e)) } }
        }
    }
    fun appearance(value: String) { viewModelScope.launch { app.preferences.appearance(value) } }
    fun language(value: String) { viewModelScope.launch {
        app.preferences.language(value)
        mutable.update { it.copy(preferences=it.preferences.copy(language=value,languageChosen=true)) }
        state.value.details[state.value.route.key]?.entity?.let(::prepareDescription)
    } }
    fun unlockSecurity() { mutable.update { it.copy(securityUnlocked=true) } }
    fun revealSecret(key: String) { mutable.update { it.copy(visibleSecrets=it.visibleSecrets+key,securityUnlocked=true) } }
    fun hideSecret(key: String) { mutable.update { it.copy(visibleSecrets=it.visibleSecrets-key) } }
    fun hideSensitive() { mutable.update { it.copy(visibleSecrets=emptySet(),securityUnlocked=false) } }
    val avatarChoices get()=app.avatars.choices
    fun chooseAvatar(uri: String) {
        if(state.value.authBusy || !app.avatars.approved(uri))return
        mutable.update { it.copy(authBusy=true,formError=null) }
        val forOwner=owner
        jobs["avatar"]=viewModelScope.launch {
            try {
                val profile=state.value.user ?: return@launch
                val updated=if(profile.online)app.onlineAccounts.update(forOwner,profile.name,profile.username,profile.bio,uri,profile.email,"","")
                    else app.accounts.update(forOwner,profile.name,uri)
                if(owner!=forOwner)return@launch
                mutable.update { it.copy(user=updated,authBusy=false) }
                messages.send("Avatar saved")
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(authBusy=false,formError=error(e)) } }
        }
    }
    fun saveAccountDetails(name: String,email: String,currentPassword: String,newPassword: String,confirm: String,username: String?=null,bio: String="") {
        if(state.value.authBusy)return
        mutable.update { it.copy(authBusy=true,formError=null) }
        val forOwner=owner
        jobs["security"]=viewModelScope.launch {
            var activeOwner=forOwner
            try {
                if(email.trim().lowercase()!=state.value.user?.email || newPassword.isNotEmpty()) {
                    require(state.value.securityUnlocked) { "Unlock account details first." }
                    aiJob?.cancel()
                }
                val previous=state.value.user ?: error("Account unavailable.")
                require(newPassword.isEmpty() || newPassword==confirm) { "Passwords do not match." }
                val updated=if(previous.online)app.onlineAccounts.update(forOwner,name,username ?: previous.username,bio,previous.avatar,email,currentPassword,newPassword)
                    else app.accounts.updateDetails(forOwner,name,email,currentPassword,newPassword,confirm,username,bio)
                if(owner!=forOwner)return@launch
                owner=updated.ownerKey
                activeOwner=updated.ownerKey
                mutable.update { it.copy(user=updated) }
                if(updated.ownerKey!=forOwner)app.preferences.session(updated.ownerKey)
                mutable.update { it.copy(user=updated,authBusy=false,visibleSecrets=emptySet(),securityUnlocked=false) }
                drafts.keys.filter { it.startsWith("profile:") }.toList().forEach(drafts::remove)
                refreshLibrary();refreshChats();back();messages.send("Profile saved")
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==activeOwner)mutable.update { it.copy(authBusy=false,formError=error(e)) } }
        }
    }
    fun connectOnline(create: Boolean,email: String,password: String,confirm: String,localPassword: String,username: String,bio: String) {
        if(state.value.authBusy)return
        val local=state.value.user ?: return
        if(local.online || local.email=="guest")return
        val forOwner=owner
        mutable.update { it.copy(authBusy=true,formError=null) }
        jobs["connect"]=viewModelScope.launch {
            try {
                require(onlineAvailable) { "The online service is not configured yet." }
                app.accounts.verifyLocal(forOwner,localPassword)
                if(create)require(password==confirm) { "Passwords do not match." }
                val connected=if(create)app.onlineAccounts.register(local.name,email,password,username,bio,local.avatar)
                    else app.onlineAccounts.login(email,password)
                sessionLock.withLock {
                    currentCoroutineContext().ensureActive()
                    require(owner==forOwner) { "Account unavailable." }
                    withContext(NonCancellable) {
                        aiJob?.cancel()
                        app.onlineAccounts.bindLocal(forOwner,connected)
                        owner=connected.ownerKey;app.preferences.session(owner)
                        mutable.update { it.copy(user=connected,authBusy=false,ai=AiState()) }
                    }
                }
                drafts.keys.filter { it.startsWith("connect:") }.toList().forEach(drafts::remove)
                saved["chatId"]=null;refreshLibrary();refreshChats();navigate(Route("profile"),replace=true)
                messages.send("Online account connected")
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(authBusy=false,formError=error(e)) } }
        }
    }
    fun refreshOnlineProfile() {
        if(!onlineAvailable || !owner.startsWith("remote:"))return
        val forOwner=owner;jobs["onlineProfile"]?.cancel()
        jobs["onlineProfile"]=viewModelScope.launch {
            try {
                val profile=app.onlineAccounts.me(forOwner)
                if(owner==forOwner)mutable.update { it.copy(user=profile,onlineProfileNotice=null) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)mutable.update { it.copy(onlineProfileNotice=error(e)) } }
        }
    }
    fun loadPublicProfile(id: String) {
        val forOwner=owner
        mutable.update { it.copy(publicProfile=null,publicProfileLoading=true,publicProfileError=null) }
        jobs["publicProfile"]?.cancel()
        jobs["publicProfile"]=viewModelScope.launch {
            try {
                val profile=app.onlineAccounts.publicProfile(forOwner,id)
                if(owner==forOwner && state.value.route.userId==id)mutable.update { it.copy(publicProfile=profile,publicProfileLoading=false) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner && state.value.route.userId==id)mutable.update { it.copy(publicProfileLoading=false,publicProfileError=error(e)) } }
        }
    }
    private suspend fun refreshLibrary() { val forOwner=owner; val entries=app.library.all(forOwner); if(forOwner==owner) mutable.update { it.copy(library=entries) } }
    private suspend fun recordViewed(entity: ComicEntity) {
        try { app.library.record(owner,entity); refreshLibrary() }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(e: Exception) { messages.send("Could not update local history.") }
    }
    fun favorite(entity: ComicEntity) { viewModelScope.launch {
        try {
            val wasSaved=state.value.library.any { it.entity.id==entity.id && it.entity.type==entity.type && it.favorite }
            if(!entity.type.canFavorite && !wasSaved) return@launch
            app.library.record(owner,entity,true); refreshLibrary()
            if(!state.value.favoriteTab.canFavorite && state.value.library.none { it.favorite && it.entity.type==state.value.favoriteTab }) {
                favoriteTab(ResourceType.CHARACTER)
            }
            messages.send(if(wasSaved) "Removed from favorites" else "Saved to favorites")
        } catch(e: Exception) { messages.send("Could not save changes. Please try again.") }
    } }
    fun favoriteTab(type: ResourceType) { mutable.update { it.copy(favoriteTab=type) } }
    fun clearHistory() { viewModelScope.launch { app.library.clearHistory(owner); refreshLibrary(); messages.send("History cleared") } }

    fun loadHome() {
        jobs["home"]?.cancel()
        mutable.update { it.copy(home=it.home.copy(loading=true,error=null)) }
        jobs["home"]=viewModelScope.launch {
            try {
                val featured=app.marvel.browse(ResourceType.CHARACTER,"Wolverine")
                val stories=app.marvel.browse(ResourceType.STORY_ARC,"Civil War")
                val character=featured.items.find { it.name.equals("Wolverine",true) } ?: featured.items.firstOrNull()
                val story=stories.items.find { it.name.equals("Civil War",true) } ?: stories.items.firstOrNull()
                mutable.update { it.copy(home=BrowseState(items=listOfNotNull(character,story),offline=featured.offline||stories.offline,more=false)) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { mutable.update { it.copy(home=it.home.copy(loading=false,error=error(e))) } }
        }
    }
    fun search(query: String) {
        val route=state.value.route
        val key=if(route.screen=="catalog") route.key else "search"
        drafts["query:$key"]=query
        jobs[key]?.cancel(); generations[key]=(generations[key]?:0)+1
        val old=state.value.pages[key] ?: BrowseState()
        mutable.update { it.copy(pages=it.pages+(key to old.copy(query=query,loading=query.isNotBlank(),items=emptyList(),offset=0,error=null))) }
        jobs[key]=viewModelScope.launch {
            delay(400)
            if(route.screen=="catalog") loadPage(route,reset=true) else globalSearch(query,key)
        }
    }
    private suspend fun globalSearch(query: String,key: String) {
        if(query.isBlank()) { mutable.update { it.copy(pages=it.pages+(key to BrowseState(more=false))) }; return }
        val generation=generations[key]
        try {
            val results=app.marvel.searchResults(query)
            if(generation!=generations[key]) return
            mutable.update { it.copy(pages=it.pages+(key to BrowseState(items=results.items,query=query,more=false,offline=results.offline,error=if(results.partial) "Some categories could not be loaded. Try again to complete the search." else null)),recentSearches=(listOf(query)+it.recentSearches).distinct().take(8)) }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(e: Exception) { if(generation==generations[key]) mutable.update { it.copy(pages=it.pages+(key to BrowseState(query=query,error=error(e),more=false))) } }
    }
    fun retrySearch() { search(drafts["query:search"].orEmpty()) }
    fun searchType(type: ResourceType?) { mutable.update { it.copy(searchType=type) } }
    fun sort() {
        val route=state.value.route
        val old=state.value.pages[route.key] ?: return
        val nextSort=when(old.sort) {
            "date_last_updated:desc" -> "name:asc"
            "name:asc" -> "name:desc"
            else -> "date_last_updated:desc"
        }
        mutable.update { it.copy(pages=it.pages+(route.key to old.copy(sort=nextSort))) }
        loadPage(route,true)
    }
    fun filters(marvelOnly: Boolean,power: String) {
        val route=Route("catalog",ResourceType.CHARACTER)
        val old=state.value.pages[route.key] ?: BrowseState()
        drafts["filter:marvel"]=marvelOnly.toString()
        drafts["filter:power"]=power.trim()
        mutable.update { it.copy(pages=it.pages+(route.key to old.copy(marvelOnly=marvelOnly,power=power.trim()))) }
        if(state.value.route.screen=="filters") back()
        if(state.value.route!=route) navigate(route)
        loadPage(route,true)
    }
    fun loadPage(route: Route=state.value.route,reset: Boolean=false) {
        val key=route.key
        val old=state.value.pages[key] ?: BrowseState()
        if(!reset && (old.loading || !old.more)) return
        if(!reset && old.retryAtNanos?.let { it-System.nanoTime()>0 }==true) return
        jobs[key]?.cancel()
        val generation=(generations[key]?:0)+1; generations[key]=generation
        val base=if(reset) old.copy(items=emptyList(),offset=0,more=true) else old
        mutable.update { it.copy(pages=it.pages+(key to base.copy(loading=true,error=null,retryAtNanos=null))) }
        jobs[key]=viewModelScope.launch {
            try {
                val page=app.marvel.browse(route.type!!,base.query,base.offset,base.sort,base.marvelOnly)
                // Powers are only present in detail responses: enrich the current bounded page when filtering.
                val entities=if(base.power.isBlank()) page.items else {
                    val enriched=mutableListOf<ComicEntity>()
                    for(item in page.items) { ensureActive(); val detail=app.marvel.detail(item.type,item.id); if(detail.powers.any { it.name.contains(PowerNames.query(base.power),true) }) enriched.add(detail) }
                    enriched
                }
                if(generation!=generations[key]) return@launch
                mutable.update { it.copy(pages=it.pages+(key to base.copy(
                    items=(base.items+entities).distinctBy { item -> item.id },loading=false,
                    offset=page.nextOffset,more=page.hasMore && page.nextOffset>base.offset,offline=page.offline,error=null,retryAtNanos=null))) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) {
                val retryAt=(e as? ComicVineException)?.retryAfterSeconds?.coerceIn(0,3600)?.let { System.nanoTime()+it*1_000_000_000L }
                if(generation==generations[key]) mutable.update { it.copy(pages=it.pages+(key to base.copy(loading=false,error=error(e),retryAtNanos=retryAt))) }
            }
        }
    }
    fun open(entity: ComicEntity) {
        val route=Route("detail",entity.type,entity.id,entity.name)
        navigate(route)
    }
    fun compareCharacter(entity: ComicEntity) {
        require(entity.type==ResourceType.CHARACTER)
        mutable.update { it.copy(comparisonCharacter=entity) }
        navigate(Route("catalog",ResourceType.CHARACTER))
    }
    fun clearComparison() { mutable.update { it.copy(comparisonCharacter=null) } }
    fun open(type: ResourceType,reference: ComicReference) = navigate(Route("detail",type,reference.id,reference.name))
    fun loadDetail(route: Route=state.value.route) {
        val key=route.key
        jobs[key]?.cancel()
        mutable.update { it.copy(details=it.details+(key to DetailState(loading=true))) }
        jobs[key]=viewModelScope.launch {
            try {
                val record=app.marvel.loadDetail(route.type!!,route.id)
                mutable.update { it.copy(details=it.details+(key to DetailState(record.entity,offline=record.offline))) }
                loadRelatedIssues(route)
                prepareDescription(record.entity)
                recordViewed(record.entity)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) {
                val local=state.value.library.find { it.entity.id==route.id && it.entity.type==route.type }?.entity
                mutable.update { it.copy(details=it.details+(key to DetailState(local,error=if(local==null) error(e) else null,offline=local!=null))) }
                if(local!=null) { recordViewed(local);loadRelatedIssues(route);prepareDescription(local) }
            }
        }
    }
    fun loadRelatedIssues(route: Route=state.value.route,count: Int=8) {
        val entity=state.value.details[route.key]?.entity ?: return
        if(entity.issues.isEmpty() && entity.firstIssue==null)return
        val key=route.key
        val existing=state.value.relatedIssues[key] ?: RelatedIssuesState()
        if(existing.loading)return
        val nextCount=count.coerceAtLeast(0).coerceAtMost(entity.issues.size)
        val needed=(entity.issues.take(nextCount)+listOfNotNull(entity.firstIssue))
            .filter { it.id !in existing.records }.map { it.id }.distinct()
        mutable.update { it.copy(relatedIssues=it.relatedIssues+(key to existing.copy(count=nextCount,loading=needed.isNotEmpty(),error=null))) }
        if(needed.isEmpty())return
        jobs["issues:$key"]=viewModelScope.launch {
            try {
                val summaries=needed.chunked(100).map { app.marvel.issueSummaries(it) }
                mutable.update { current ->
                    val old=current.relatedIssues[key] ?: existing
                    current.copy(relatedIssues=current.relatedIssues+(key to old.copy(
                        records=old.records+summaries.flatMap { it.items }.associateBy { it.id },
                        loading=false,offline=summaries.any { it.offline })))
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) {
                mutable.update { current ->
                    val old=current.relatedIssues[key] ?: existing
                    current.copy(relatedIssues=current.relatedIssues+(key to old.copy(loading=false,error=error(e))))
                }
            }
        }
    }
    fun ask(entity: ComicEntity?) {
        aiJob?.cancel()
        suggestionSource=null
        mutable.update { it.copy(ai=AiState(context=entity,sources=listOfNotNull(entity))) }
        drafts["chat"]=""
        saved["chatId"]=null
        scrollPositions.remove(Route("ai").key)
        navigate(Route("ai"))
    }
    private suspend fun refreshChats() {
        val forOwner=owner
        try {
            val list=app.chats.list(forOwner)
            if(owner==forOwner)mutable.update { it.copy(chats=list,chatHistoryLoading=false,chatHistoryError=null) }
        } catch(e: Exception) { mutable.update { it.copy(chatHistoryLoading=false,chatHistoryError=error(e)) } }
    }
    fun loadChatHistory() {
        mutable.update { it.copy(chatHistoryLoading=true,chatHistoryError=null) }
        viewModelScope.launch { refreshChats() }
    }
    fun openConversation(id: String,navigate: Boolean=true) {
        aiJob?.cancel();suggestionSource=null
        val forOwner=owner
        viewModelScope.launch {
            try {
            val snapshot=app.chats.load(forOwner,id)
            if(owner!=forOwner)return@launch
            if(snapshot==null) { messages.send("Conversation unavailable.");return@launch }
            mutable.update { it.copy(ai=AiState(context=snapshot.context,messages=snapshot.messages,sources=snapshot.sources,
                conversationId=snapshot.id,error=if(snapshot.messages.lastOrNull()?.role=="user")"The previous reply was interrupted. Try again." else null)) }
            saved["chatId"]=snapshot.id;drafts["chat"]=""
            if(navigate)navigate(Route("ai"))
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)messages.send("Conversation unavailable.") }
        }
    }
    fun deleteConversation(id: String) {
        val forOwner=owner
        viewModelScope.launch {
            try {
            app.chats.delete(forOwner,id)
            if(owner==forOwner) {
                if(state.value.ai.conversationId==id) { aiJob?.cancel();mutable.update { it.copy(ai=AiState()) };saved["chatId"]=null }
                refreshChats()
            }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { if(owner==forOwner)messages.send("Could not save changes. Please try again.") }
        }
    }
    private suspend fun persistChat(ai: AiState,forOwner: String) {
        val id=ai.conversationId ?: return
        val title=ai.messages.firstOrNull { it.role=="user" }?.text?.replace(Regex("\\s+")," ")?.take(70) ?: return
        app.chats.save(forOwner,ChatSnapshot(id,title,ai.context,ai.messages,ai.sources))
        if(owner==forOwner)refreshChats()
    }
    fun suggestQuestion(type: ResourceType,query: String,prompt: String) {
        ask(null)
        suggestionSource=type to query
        sendChat(prompt)
    }
    fun sendChat(text: String, retry: Boolean=false) {
        val current=state.value.ai
        val prompt=text.trim()
        if(current.sending || prompt.isBlank()) return
        val history=if(retry) current.messages else current.messages+ChatMessage("user",prompt)
        drafts["chat"]=""
        val id=current.conversationId ?: java.util.UUID.randomUUID().toString()
        val forOwner=owner
        mutable.update { it.copy(ai=current.copy(messages=history,sending=true,error=null,conversationId=id)) }
        saved["chatId"]=id
        val suggested=suggestionSource.takeIf { current.messages.none { it.role=="model" } }
        aiJob=viewModelScope.launch {
            try {
                persistChat(state.value.ai,forOwner)
                val sources=app.aiContext.records(prompt,current.context,current.sources,suggested)
                val nextContext=if(current.context!=null || suggested!=null)sources.firstOrNull() else null
                if(owner!=forOwner || state.value.ai.conversationId!=id)return@launch
                mutable.update { it.copy(ai=it.ai.copy(context=nextContext,sources=sources)) }
                persistChat(state.value.ai,forOwner)
                val answer=app.ai.reply(history,sources,state.value.preferences.language)
                if(owner!=forOwner || state.value.ai.conversationId!=id)return@launch
                mutable.update { it.copy(ai=it.ai.copy(context=nextContext,messages=history+ChatMessage("model",answer),sending=false,sources=sources)) }
                persistChat(state.value.ai,forOwner)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { mutable.update { it.copy(ai=it.ai.copy(sending=false,error=error(e))) } }
        }
    }
    fun translationKey(text: String)=app.translations.key(text)
    fun translateCatalog(text: String,retry: Boolean=false) {
        if(text.isBlank() || state.value.preferences.language!="pt")return
        val key=translationKey(text)
        if(!retry && state.value.translatedTexts.containsKey(key))return
        if(state.value.translatedTexts[key]?.loading==true)return
        mutable.update { it.copy(translatedTexts=it.translatedTexts+(key to CatalogTextState(loading=true))) }
        jobs["translate:$key"]=viewModelScope.launch {
            try {
                val translated=app.translations.portuguese(text)
                mutable.update { it.copy(translatedTexts=it.translatedTexts+(key to CatalogTextState(text=translated))) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { mutable.update { it.copy(translatedTexts=it.translatedTexts+(key to CatalogTextState(error=error(e)))) } }
        }
    }
    fun prepareDescription(entity: ComicEntity) {
        if(state.value.preferences.language!="pt" || "${entity.type}:${entity.id}" in state.value.originalDescriptions)return
        val summary=cleanCatalogText(entity.summary.orEmpty())
        val pages=descriptionPages(cleanCatalogText(entity.descriptionHtml.orEmpty()).ifBlank { summary })
        if(summary.isNotBlank())translateCatalog(descriptionPages(summary).first())
        val index=state.value.descriptionPage[Route("detail",entity.type,entity.id).key] ?: if(summary.isBlank())0 else -1
        pages.getOrNull(index)?.let { translateCatalog(it) }
    }
    fun descriptionPage(index: Int) {
        val key=state.value.route.key
        mutable.update { it.copy(descriptionPage=it.descriptionPage+(key to index)) }
        state.value.details[key]?.entity?.let(::prepareDescription)
    }
    fun toggleOriginal(entity: ComicEntity) {
        val key="${entity.type}:${entity.id}"
        mutable.update { it.copy(originalDescriptions=if(key in it.originalDescriptions)it.originalDescriptions-key else it.originalDescriptions+key) }
        prepareDescription(entity)
    }
}
