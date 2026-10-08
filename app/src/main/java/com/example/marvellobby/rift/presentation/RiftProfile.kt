package com.example.marvellobby.rift.presentation

import com.example.marvellobby.presentation.*
import com.example.marvellobby.rift.engine.RiftCharacters

fun ScreenRenderer.riftProfile(id: String?=state.user?.id) {
    val key=id ?: state.user?.ownerKey ?: return
    label("RIFT ARENA")
    val stats=state.riftStats[key]
    if(stats==null && state.riftErrors[key]==null)vm.loadRiftStats(id)
    if(stats!=null) {
        if(stats.runs>0) {
            add(ui.text("${ui.translate("Main") } · ${RiftCharacters.get(stats.main?:"spider-man").name}",18,bold=true),gap=8)
            add(ui.text("${ui.translate("Personal best")}: ${stats.best} · ${ui.translate("Runs")}: ${stats.runs}",14,ui.palette.secondary),gap=10)
            add(ui.text("${ui.translate("Survival")}: ${stats.survival.toInt()/60}:${(stats.survival.toInt()%60).toString().padStart(2,'0')} · ${ui.translate("Kills")}: ${stats.kills} · Bosses: ${stats.bosses}",13,ui.palette.muted),gap=10)
            stats.globalPosition?.let { add(ui.text("Global · #$it",16,ui.palette.secondary,true),gap=10) }
        } else body("No ranked runs yet")
    }
    state.riftErrors[key]?.let { body(it);button("Refresh",false) { vm.loadRiftStats(id) } }
    menu("Rift Arena","${ui.translate("Characters")} · ${RiftCharacters.all.size} · ${ui.translate("Survival")}") { activity.openRift() }
    if(id!=null && id!=state.user?.id && state.user?.online==true && activity.community.state.value.profiles[id]?.isFollowing==true)
        button("Challenge in Rift Arena",false) { activity.openRift(id) }
}
