package com.example.marvellobby.presentation.social

import com.example.marvellobby.presentation.ScreenRenderer
import java.text.DateFormat
import java.util.Date

fun ScreenRenderer.cloudSync() {
    if(communitySessionRequired())return
    title("Cloud synchronization")
    body("Optionally save your viewing history and AI conversations privately in your account to access them on another device.")
    body("Catalog records use compact previews. Full details continue to load from Comic Vine. AI message text is preserved.")
    body("Pausing stops synchronization on all devices. Previously uploaded copies stay private in your account.")
    val sync=activity.community.state.value.cloud
    label(if(sync.enabled==true)"Synchronization active" else "Synchronization paused")
    if(sync.busy)add(ui.loading("Synchronizing…"))
    sync.error?.let { body(it);button("Try again",false) { activity.community.syncCloud() } }
    if(!sync.busy && sync.enabled!=null)button(if(sync.enabled)"Pause synchronization" else "Enable private synchronization") { activity.community.syncCloud(!sync.enabled) }
    if(sync.enabled==true && !sync.busy)button("Sync now",false) { activity.community.syncCloud() }
    if(sync.pending>0)body("${ui.translate("Pending records")}: ${sync.pending}")
    if(sync.lastSync>0)body("${ui.translate("Last synchronization")}: ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(sync.lastSync))}")
    if(sync.recovered)body("Changes from both devices were preserved as separate conversations in your conversation history.")
    body("Deleting history or an AI conversation also removes its cloud copy when synchronization is active. Favorites and direct messages are unaffected.")
}
