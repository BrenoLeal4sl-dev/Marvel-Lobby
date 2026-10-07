package com.example.marvellobby.rift.presentation

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import com.example.marvellobby.presentation.*
import com.example.marvellobby.rift.engine.*
import com.example.marvellobby.rift.render.RiftGameView

/** Dedicated full-screen host. Gameplay is local; no network work occurs in its frame loop. */
class RiftActivity: AppCompatActivity() {
    private lateinit var vm: RiftViewModel
    private lateinit var host: FrameLayout
    private lateinit var ui: UiKit
    private var game: RiftGameView?=null
    private var overlay: View?=null
    private var foreground=false
    private val language get()=intent.getStringExtra("language") ?: "pt"
    private fun t(en: String,pt: String)=if(language=="pt")pt else en
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        vm=ViewModelProvider(this)[RiftViewModel::class.java]
        ui=UiKit(this,Palette(false)) { Translations.text(it,language) }
        host=FrameLayout(this).apply { setBackgroundColor(0xFF090E17.toInt()) };setContentView(host)
        WindowCompat.getInsetsController(window,window.decorView).apply { isAppearanceLightStatusBars=false;isAppearanceLightNavigationBars=false }
        ViewCompat.setOnApplyWindowInsetsListener(host) { v,insets -> val b=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);insets }
        onBackPressedDispatcher.addCallback(this,object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if(vm.state.value.page!="game") { if(vm.state.value.page=="lobby")finish()else vm.page("lobby") }
                else if(vm.engine?.phase==RunPhase.FINISHED)vm.abandon()
                else { vm.engine?.pause();vm.engine?.let { showPhase(it.phase) } }
            }
        })
        vm.initialize(intent.getStringExtra("owner") ?: "guest")
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { vm.state.collect {
            if(it.page=="game" && vm.engine!=null) {
                if(game?.engine!==vm.engine)attachGame(vm.engine!!)
                else if(vm.engine!!.phase==RunPhase.FINISHED)showPhase(RunPhase.FINISHED)
            } else showLobby()
        } } }
        intent.getStringExtra("challengeTarget")?.let { vm.challenge(it);intent.removeExtra("challengeTarget") }
        intent.getStringExtra("challengeId")?.let { vm.selectChallenge(it);intent.removeExtra("challengeId") }
    }
    override fun onResume() { super.onResume();foreground=true;if(vm.engine?.phase in listOf(RunPhase.PLAYING,RunPhase.DYING))game?.startFrames() }
    override fun onPause() { foreground=false;vm.engine?.pause();game?.stopFrames();if(vm.state.value.page=="game")vm.engine?.let { showPhase(it.phase) };super.onPause() }
    private fun showLobby() {
        game?.stopFrames();host.removeAllViews();game=null;overlay=null
        val state=vm.state.value
        val column=ui.column(24).apply { gravity=Gravity.CENTER_VERTICAL;background=ui.gradient(0xFF090E17.toInt(),0xFF451023.toInt(),0) }
        if(state.page!="lobby") {
            ui.add(column,ui.button(t("Back to arena","Voltar à arena"),false) { vm.page("lobby") },0,48)
            when(state.page) { "ranking"->ranking(column);"history"->history(column);"challenges"->challenges(column) }
            if(state.busy)ui.add(column,ui.loading(),20)
            state.notice?.let { ui.add(column,ui.text(it,14,ui.palette.secondary),20) }
            host.addView(ScrollView(this).apply { isFillViewport=true;addView(column) },FrameLayout.LayoutParams(-1,-1));return
        }
        ui.add(column,ui.label("MARVEL LOBBY / RIFT ARENA"),0)
        ui.add(column,ui.text(t("Break the loop.\nOwn the rift.","Quebre o ciclo.\nDomine a fenda."),34,bold=true),16)
        ui.add(column,HeroPreview(this),20,180)
        ui.add(column,ui.text("Spider-Man",26,bold=true),20)
        ui.add(column,ui.text(t("Webs. Mobility. Control.","Teias. Mobilidade. Controle."),14,ui.palette.secondary),8)
        ui.add(column,ui.text(t("Move with the left stick. Hold WEB to attack. Dash through danger; burst to trap enemies. Pick upgrades and survive the Sentinel.","Mova com o controle esquerdo. Segure TEIA para atacar. Esquive do perigo e use EXPLOSÃO para prender inimigos. Escolha upgrades e sobreviva à Sentinela."),14,ui.palette.muted),16)
        ui.add(column,ui.text("${t("Personal best","Recorde local")} ${state.stats.best} · ${t("Runs","Partidas")} ${state.stats.runs}",16,ui.palette.secondary),16)
        ui.add(column,ui.button(t("ENTER THE RIFT","ENTRAR NA FENDA")) { vm.start() }.apply { tag="rift:play";isEnabled=!state.busy },24,56)
        if(vm.online)ui.add(column,ui.button(t("Offline practice","Treino offline"),false) { vm.start(practice=true) },12,52)
        if(state.busy)ui.add(column,ui.loading(),12)
        state.notice?.let { ui.add(column,ui.text(it,14,ui.palette.secondary),16) }
        val tabs=ui.row()
        listOf("ranking" to t("Ranking","Ranking"),"challenges" to t("Challenges","Desafios"),"history" to t("History","Histórico")).forEach { (page,label)->
            tabs.addView(ui.button(label,false) { vm.page(page) },LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=ui.dp(6) })
        }
        ui.add(column,tabs,20)
        ui.add(column,ui.button(t("View character","Ver personagem"),false) { openLobby("character",RiftCharacters.spider.catalogId.toString()) },16,52)
        ui.add(column,ui.text(t("Playable slice · provisional sprites", "Versão jogável · sprites provisórios"),11,ui.palette.muted),16)
        ui.add(column,ui.button(t("Back to Marvel Lobby","Voltar ao Marvel Lobby"),false) { finish() },16,52)
        host.addView(ScrollView(this).apply { isFillViewport=true;addView(column) },FrameLayout.LayoutParams(-1,-1))
    }
    private fun attachGame(engine: RiftEngine) {
        game?.stopFrames();host.removeAllViews();overlay=null
        game=RiftGameView(this,engine,language,::showPhase)
        host.addView(game,FrameLayout.LayoutParams(-1,-1))
        if(foreground)game!!.startFrames()
        showPhase(engine.phase)
    }
    private fun showPhase(phase: RunPhase) {
        overlay?.let { host.removeView(it) };overlay=null
        if(phase==RunPhase.PLAYING||phase==RunPhase.DYING) { if(foreground)game?.startFrames();return }
        game?.stopFrames()
        val engine=vm.engine ?: return
        val backdrop=FrameLayout(this).apply { setBackgroundColor(0xE0090E17.toInt());isClickable=true }
        val panel=ui.column(24)
        when(phase) {
            RunPhase.UPGRADE -> {
                ui.add(panel,ui.label("LEVEL ${engine.level}"),0)
                ui.add(panel,ui.title(t("Choose your next move","Escolha seu próximo movimento")),12)
                engine.choices.forEach { upgrade ->
                    val description=upgrade.description.split(" / ").let { if(language=="pt")it.last()else it.first() }
                    val rarity=if(language=="pt")when(upgrade.rarity) { Rarity.COMMON->"COMUM";Rarity.RARE->"RARO";Rarity.EPIC->"ÉPICO";Rarity.LEGENDARY->"LENDÁRIO" }else upgrade.rarity.name
                    val choice=ui.column(16).apply { background=ui.shape(ui.palette.raised,border=true) }
                    val tint=when(upgrade.rarity) {Rarity.COMMON->0xFFBDCBDA.toInt();Rarity.RARE->0xFF75B6FF.toInt();Rarity.EPIC->0xFFD0A0FF.toInt();Rarity.LEGENDARY->0xFFFFCF72.toInt()}
                    ui.add(choice,ui.text("$rarity · ${engine.rank(upgrade)+1}/${upgrade.cap}",12,tint,true),0)
                    ui.add(choice,ui.text(upgrade.title,20,bold=true),8)
                    ui.add(choice,ui.text(description,13,ui.palette.muted),8)
                    ui.add(choice,ui.button(t("Choose","Escolher")) { if(engine.choose(upgrade))showPhase(engine.phase) },14,48)
                    ui.add(panel,choice,16)
                }
            }
            RunPhase.PAUSED -> {
                ui.add(panel,ui.label("RIFT ARENA"),0);ui.add(panel,ui.title(t("Take a breath.","Respire.")),16)
                ui.add(panel,ui.text(t("The arena waits for you.","A arena espera por você.")),12)
                ui.add(panel,ui.button(t("Resume","Continuar")) { engine.resume();showPhase(engine.phase) },24,56)
                ui.add(panel,ui.button(t("Restart","Reiniciar"),false) { confirmRestart() },16,52)
                ui.add(panel,ui.button(t("Leave run","Sair da partida"),false) { confirmLeave() },16,52)
            }
            RunPhase.FINISHED -> {
                vm.finished()
                val result=engine.result()
                ui.add(panel,ui.label(t(if(result.extracted)"RIFT STABILIZED" else "RUN ENDED",if(result.extracted)"FENDA ESTABILIZADA" else "PARTIDA ENCERRADA")),0)
                ui.add(panel,ui.text(result.score.toString(),54,ui.palette.secondary,true),16)
                ui.add(panel,ui.text(t("SCORE","PONTUAÇÃO"),12),6)
                val minutes=result.duration.toInt()/60;val seconds=(result.duration.toInt()%60).toString().padStart(2,'0')
                ui.add(panel,ui.text("$minutes:$seconds · ${result.kills} ${t("kills","eliminações")} · LV ${result.level}",16),20)
                ui.add(panel,ui.text("${result.bosses} bosses · ${result.elites} ${t("elites","elites")} · ${t("Max combo","Maior combo")} ${result.maxCombo}",14,ui.palette.muted),12)
                ui.add(panel,ui.text(t("Time ×10 + kills ×40 + elites ×150 + bosses ×1200 + damage ÷5 + max combo ×20", "Tempo ×10 + eliminações ×40 + elites ×150 + bosses ×1200 + dano ÷5 + maior combo ×20"),12,ui.palette.muted),16)
                if(vm.state.value.newRecord)ui.add(panel,ui.label(t("NEW PERSONAL BEST","NOVO RECORDE LOCAL")),16)
                val status=when(vm.state.value.resultStatus) {"synced"->t("Validated result · included in rankings","Resultado validado · incluído no ranking");"pending"->t("Saved · waiting for synchronization","Salvo · aguardando sincronização");"rejected"->t("Local result · not eligible for ranking","Resultado local · não elegível para o ranking");else->t("Practice · local result","Treino · resultado local")}
                ui.add(panel,ui.text(status,12,ui.palette.secondary),16)
                ui.add(panel,ui.button(t("PLAY AGAIN","JOGAR NOVAMENTE")) { vm.abandon(next=true) },24,56)
                ui.add(panel,ui.button(t("View ranking","Ver ranking"),false) { vm.page("ranking") },12,52)
                if(vm.state.value.resultStatus=="synced")vm.session?.id?.let { id->ui.add(panel,ui.button(t("Share result","Compartilhar resultado"),false) { openLobby("shareResult",id,result.score) },12,52) }
                ui.add(panel,ui.button(t("Back to Marvel Lobby","Voltar ao Marvel Lobby"),false) { finish() },16,52)
            }
            else -> Unit
        }
        backdrop.addView(ScrollView(this).apply { isFillViewport=true;addView(panel) },FrameLayout.LayoutParams(-1,-2,Gravity.CENTER))
        host.addView(backdrop,FrameLayout.LayoutParams(-1,-1));overlay=backdrop
    }
    private fun confirmRestart() { androidx.appcompat.app.AlertDialog.Builder(this).setTitle(t("Restart run?","Reiniciar partida?"))
        .setMessage(t("Your current run will be discarded.","Sua partida atual será descartada."))
        .setPositiveButton(t("Restart","Reiniciar")) { _,_->vm.abandon(next=true) }.setNegativeButton(t("Cancel","Cancelar"),null).show() }
    private fun confirmLeave() { androidx.appcompat.app.AlertDialog.Builder(this).setTitle(t("Leave run?","Sair da partida?"))
        .setMessage(t("Your current run will be discarded.","Sua partida atual será descartada."))
        .setPositiveButton(t("Leave","Sair")) { _,_->vm.abandon() }.setNegativeButton(t("Cancel","Cancelar"),null).show() }
    private fun openLobby(action: String,id: String,score: Int?=null) {
        startActivity(android.content.Intent(this,com.example.marvellobby.MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("riftAction",action).putExtra("riftTarget",id).putExtra("riftScore",score ?: -1));finish()
    }
    private fun ranking(column: LinearLayout) {
        val state=vm.state.value;ui.add(column,ui.title("Rift Ranking"),20)
        val tabs=ui.row()
        listOf("global" to "Global","friends" to t("Following","Seguidos"),"weekly" to t("Weekly","Semanal"),"character" to "Spider-Man").forEach { (mode,label)->
            tabs.addView(ui.button(label,state.mode==mode) { vm.ranking(mode) },LinearLayout.LayoutParams(-2,-2).apply { marginEnd=ui.dp(8) })
        }
        ui.add(column,HorizontalScrollView(this).apply { addView(tabs);isHorizontalScrollBarEnabled=false },16)
        ui.add(column,ui.button(t("Refresh","Atualizar"),false) { vm.ranking() },16,48)
        val page=state.rank
        ui.add(column,ui.text(page?.ownPosition?.let { "${t("Your position","Sua posição")} #$it · ${page.ownScore}" } ?: t("Complete a competitive run to join the ranking.","Complete uma partida competitiva para entrar no ranking."),14,ui.palette.secondary),20)
        if(page!=null&&page.items.isEmpty())ui.add(column,ui.text(t("The rift is waiting for its first challenger.","A fenda espera seu primeiro competidor.")),16)
        page?.items?.forEach { row->ui.add(column,ui.menu("#${row.position}  @${row.username}","${row.score} · ${row.name}") { openLobby("profile",row.userId) },12) }
        if(page?.next!=null)ui.add(column,ui.button(t("Load more","Carregar mais"),false) { vm.ranking(more=true) },16,48)
    }
    private fun history(column: LinearLayout) {
        ui.add(column,ui.title(t("Run history","Histórico de partidas")),20)
        ui.add(column,ui.text(t("Latest 100 runs. Profile totals include every saved run.","Últimas 100 partidas. Os totais do perfil incluem todas as partidas salvas."),12,ui.palette.muted),12)
        ui.add(column,ui.button(t("Synchronize results","Sincronizar resultados"),false) { vm.refresh(sync=true) },16,52)
        if(vm.state.value.records.isEmpty())ui.add(column,ui.text(t("Your first run starts here.","Sua primeira partida começa aqui.")),16)
        vm.state.value.records.forEach { row ->
            val result=(application as com.example.marvellobby.MarvelApplication).rift.result(row)
            val timestamp=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT,if(language=="pt")java.util.Locale.forLanguageTag("pt-BR")else java.util.Locale.US).format(java.util.Date(row.endedAt))
            val status=when(row.status) {"synced"->t("Ranked","No ranking");"pending"->t("Pending upload","Envio pendente");"rejected"->t("Local only","Somente local");else->t("Practice","Treino")}
            val box=ui.column(16).apply { background=ui.shape(ui.palette.surface) }
            ui.add(box,ui.text("${result.score} · $status",22,bold=true),0)
            ui.add(box,ui.text("$timestamp\n${result.duration.toInt()/60}:${(result.duration.toInt()%60).toString().padStart(2,'0')} · ${result.kills} ${t("kills","eliminações")} · LV ${result.level}",13,ui.palette.muted),10)
            if(result.upgrades.isNotEmpty())ui.add(box,ui.text(result.upgrades.entries.joinToString(" · ") { "${Upgrade.valueOf(it.key).title} ${it.value}" },12,ui.palette.secondary),10)
            if(row.status=="synced" && row.sessionId!=null)ui.add(box,ui.button(t("Share result","Compartilhar resultado"),false) { openLobby("shareResult",row.sessionId!!,result.score) },12,48)
            ui.add(column,box,16)
        }
    }
    private fun challenges(column: LinearLayout) {
        ui.add(column,ui.title(t("Rift Challenges","Desafios da Rift")),20)
        ui.add(column,ui.text(t("Challenge someone you follow from their profile. Same seed, arena and director. One attempt per player; ties share the victory.","Desafie alguém que você segue pelo perfil. Mesma seed, arena e progressão. Uma tentativa por jogador; empates dividem a vitória."),14,ui.palette.muted),16)
        ui.add(column,ui.button(t("Refresh","Atualizar"),false) { vm.challenges() },16,48)
        if(vm.state.value.challengeId!=null)ui.add(column,ui.button(t("All challenges","Todos os desafios"),false) { vm.selectChallenge(null) },12,48)
        val page=vm.state.value.challenges
        if(page!=null&&page.items.isEmpty())ui.add(column,ui.text(t("No challenges yet.","Nenhum desafio ainda.")),16)
        page?.items?.forEach { challenge ->
            val box=ui.column(16).apply { background=ui.shape(ui.palette.surface) }
            ui.add(box,ui.text("@${challenge.challenger}  VS  @${challenge.challenged}",18,bold=true),0)
            ui.add(box,ui.text("${t("You","Você")}: ${challenge.ownScore ?: "—"}  ·  ${t("Opponent","Oponente")}: ${challenge.peerScore ?: "—"}",14,ui.palette.secondary),12)
            val verdict=if(challenge.ownScore!=null&&challenge.peerScore!=null)when {challenge.ownScore>challenge.peerScore->t("You won","Você venceu");challenge.ownScore<challenge.peerScore->t("Opponent won","Oponente venceu");else->t("Tie","Empate")}else t("Waiting for results","Aguardando resultados")
            ui.add(box,ui.text(verdict,13),12)
            val expired=java.time.Instant.parse(challenge.expiresAt).toEpochMilli()<System.currentTimeMillis()
            if(challenge.ownScore==null&&!expired&&!challenge.attempted)ui.add(box,ui.button(t("Play challenge","Jogar desafio")) { vm.start(challengeId=challenge.id) }.apply { isEnabled=!vm.state.value.busy },16,52)
            if(challenge.attempted&&challenge.ownScore==null)ui.add(box,ui.text(t("Attempt started. Finish the current run; abandoning uses your attempt.","Tentativa iniciada. Finalize a partida atual; abandonar consome sua tentativa."),12,ui.palette.muted),12)
            if(expired)ui.add(box,ui.text(t("Expired","Expirado"),12,ui.palette.muted),12)
            if(!expired)ui.add(box,ui.button(t("Share challenge","Compartilhar desafio"),false) { openLobby("shareChallenge",challenge.id) },12,48)
            ui.add(column,box,16)
        }
        if(page?.next!=null)ui.add(column,ui.button(t("Load more","Carregar mais"),false) { vm.challenges(more=true) },16,48)
    }
}

/** Lobby preview shares the same provisional silhouette language without starting a simulation. */
private class HeroPreview(context: android.content.Context): View(context) {
    private val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: android.graphics.Canvas) {
        c.save();c.translate(width/2f,height/2f);val s=height/120f;c.scale(s,s)
        paint.color=0x334C6CF0;c.drawCircle(0f,0f,50f,paint)
        paint.color=0xFF2859AE.toInt();paint.strokeWidth=14f;paint.strokeCap=android.graphics.Paint.Cap.ROUND
        c.drawLine(-7f,8f,-15f,40f,paint);c.drawLine(7f,8f,15f,40f,paint)
        c.drawRoundRect(-18f,-24f,18f,16f,10f,10f,paint)
        paint.color=0xFFF02A3D.toInt();c.drawRoundRect(-11f,-24f,11f,5f,6f,6f,paint)
        c.drawLine(-16f,-17f,-32f,2f,paint);c.drawLine(16f,-17f,32f,2f,paint);c.drawCircle(0f,-35f,16f,paint)
        paint.color=0xFFF3F4FC.toInt();paint.strokeWidth=5f;c.drawLine(-11f,-39f,-4f,-33f,paint);c.drawLine(11f,-39f,4f,-33f,paint)
        c.restore()
    }
}
