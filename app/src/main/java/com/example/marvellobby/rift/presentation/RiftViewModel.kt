package com.example.marvellobby.rift.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.marvellobby.MarvelApplication
import com.example.marvellobby.rift.engine.*
import com.example.marvellobby.rift.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.security.SecureRandom
import java.util.UUID

data class RiftUiState(val page: String="lobby",val busy: Boolean=false,val notice: String?=null,
    val stats: RiftStats=RiftStats(),val records: List<StoredRiftRun> = emptyList(),val rank: RiftRankPage?=null,
    val mode: String="global",val challenges: RiftChallengePage?=null,val newRecord: Boolean=false,val resultStatus: String="",
    val challengeId: String?=null)
class RiftViewModel(application: Application): AndroidViewModel(application) {
    private val repo=(application as MarvelApplication).rift
    private val preferences=com.example.marvellobby.data.local.PreferencesStore(application)
    private var tutorialComplete=false
    private var tutorialLoaded=false
    private var afterTutorial: Pair<Boolean,String?>?=null
    private val mutable=MutableStateFlow(RiftUiState());val state=mutable.asStateFlow()
    var engine: RiftEngine?=null;private set
    var session: RiftSession?=null;private set
    private var owner="";private var runId="";private var savedId="";private var requestId=UUID.randomUUID().toString()
    private var job: Job?=null
    val online get()=owner.startsWith("remote:")
    fun initialize(owner: String) {if(this.owner.isNotEmpty())return;this.owner=owner;mutable.update{it.copy(busy=true)};viewModelScope.launch {tutorialComplete=preferences.riftTutorial(owner).first();tutorialLoaded=true;if(job==null)refresh()else {val records=repo.history(owner);val stats=repo.localStats(owner);mutable.update{it.copy(records=records,stats=stats)}}} }
    fun replayTutorial() {if(!tutorialLoaded)return;afterTutorial=null;session=null;engine=RiftEngine(1443,tutorial=true);mutable.update {it.copy(page="game",notice=null)} }
    fun finishTutorial() {
        if(engine?.tutorial!=true||engine?.tutorialStep!=6)return
        engine=null
        viewModelScope.launch {
            preferences.finishRiftTutorial(owner);tutorialComplete=true
            val next=afterTutorial;afterTutorial=null;mutable.update {it.copy(page="lobby",busy=false)}
            if(next!=null)start(next.first,next.second)else refresh()
        }
    }
    private fun notice(error: Exception)=when {
        error is com.example.marvellobby.data.remote.LobbyApiException && error.status==401 -> "Sign in again to use competitive play."
        error is com.example.marvellobby.data.remote.LobbyApiException && error.status==409 -> "The previous competitive session is still active. Finish it or play practice."
        else -> "Arena service unavailable. Your local results are kept. Try again or play practice."
    }
    fun refresh(sync: Boolean=false) {job?.cancel();job=viewModelScope.launch {
        mutable.update { it.copy(busy=true,notice=null) }
        try {
            val records=repo.history(owner);val stats=repo.localStats(owner)
            mutable.update { it.copy(records=records,stats=stats) }
            if(online && sync)repo.sync(owner)
            val updatedRecords=repo.history(owner);mutable.update { it.copy(busy=false,records=updatedRecords) }
        } catch(e: CancellationException) {throw e} catch(e: Exception) {mutable.update { it.copy(busy=false,notice=notice(e)) }}
    } }
    fun page(page: String) {mutable.update { it.copy(page=page,notice=null) };when(page) {"history","lobby"->refresh();"ranking"->ranking();"challenges"->challenges()}}
    fun start(practice: Boolean=false,challengeId: String?=null) {
        if(!tutorialLoaded)return
        if(mutable.value.busy && !practice)return
        if(!tutorialComplete) {afterTutorial=practice to challengeId;session=null;engine=RiftEngine(1443,tutorial=true);mutable.update {it.copy(page="game",busy=false,notice=null)};return}
        job?.cancel();job=viewModelScope.launch {
            mutable.update { it.copy(busy=true,notice=null,challengeId=challengeId) }
            try {
                session=if(!practice && online)repo.session(owner,requestId,challengeId)else null
                engine=RiftEngine(session?.seed ?: SecureRandom().nextInt())
                runId=UUID.randomUUID().toString();savedId="";requestId=UUID.randomUUID().toString()
                mutable.update { it.copy(page="game",busy=false,newRecord=false,resultStatus=if(session==null)"practice" else "competitive") }
            } catch(e: CancellationException) {throw e} catch(e: Exception) {mutable.update { it.copy(busy=false,notice=notice(e)) }}
        }
    }
    fun finished() {
        val current=engine ?: return
        if(current.phase!=RunPhase.FINISHED||savedId==runId)return
        savedId=runId;val id=runId;val issued=session;val result=current.result();val prior=state.value.stats.best
        viewModelScope.launch {
            repo.save(owner,id,result,issued)
            if(id==runId)mutable.update { it.copy(newRecord=result.score>prior,resultStatus=if(issued==null)"practice" else "pending") }
            try {if(online)repo.sync(owner)}catch(e: CancellationException){throw e}catch(_: Exception) {mutable.update { it.copy(notice="Arena service unavailable. Your local results are kept. Try again or play practice.") }}
            val records=repo.history(owner);val status=records.firstOrNull { it.id==id }?.status.orEmpty();val stats=repo.localStats(owner)
            mutable.update { it.copy(records=records,stats=stats,resultStatus=if(id==runId)status else it.resultStatus) }
        }
    }
    fun abandon(next: Boolean=false) {
        val old=session;val completed=engine?.phase==RunPhase.FINISHED
        engine=null;session=null
        mutable.update { it.copy(page="lobby",busy=true,notice=null) }
        job?.cancel();job=viewModelScope.launch {
            try {if(old!=null&&!completed)repo.abandon(owner,old.id)}catch(e: CancellationException){throw e}catch(_: Exception) { }
            mutable.update { it.copy(busy=false) };if(next)start()else refresh()
        }
    }
    fun ranking(mode: String=state.value.mode,more: Boolean=false) {
        if(!online) {mutable.update { it.copy(notice="Sign in again to use competitive play.") };return}
        job?.cancel();job=viewModelScope.launch {
            val prior=state.value.rank;val offset=if(more)prior?.next ?: return@launch else 0
            mutable.update { it.copy(busy=true,mode=mode,notice=null,rank=if(more)prior else null) }
            try {val page=repo.ranking(owner,mode,offset);mutable.update { it.copy(busy=false,rank=if(more)page.copy(items=prior!!.items+page.items)else page) }}
            catch(e: CancellationException){throw e}catch(e: Exception){mutable.update { it.copy(busy=false,notice=notice(e)) }}
        }
    }
    fun challenges(more: Boolean=false) {
        if(!online) {mutable.update { it.copy(notice="Sign in again to use competitive play.") };return}
        job?.cancel();job=viewModelScope.launch {
            val prior=state.value.challenges;val offset=if(more)prior?.next ?: return@launch else 0
            mutable.update { it.copy(busy=true,notice=null) }
            try {val page=repo.challenges(owner,offset,state.value.challengeId);mutable.update { it.copy(busy=false,challenges=if(more)page.copy(items=prior!!.items+page.items)else page) }}
            catch(e: CancellationException){throw e}catch(e: Exception){mutable.update { it.copy(busy=false,notice=notice(e)) }}
        }
    }
    fun selectChallenge(id: String?) { mutable.update { it.copy(challengeId=id?.let { value->UUID.fromString(value).toString() },page="challenges") };challenges() }
    fun challenge(target: String) {
        job?.cancel();job=viewModelScope.launch {
            mutable.update { it.copy(page="challenges",busy=true,notice=null) }
            try {val challenge=repo.challenge(owner,target,requestId);requestId=UUID.randomUUID().toString();mutable.update { it.copy(busy=false,challengeId=challenge.getString("id")) };challenges()}
            catch(e: CancellationException){throw e}catch(e: Exception){mutable.update { it.copy(busy=false,notice=notice(e)) }}
        }
    }
}
