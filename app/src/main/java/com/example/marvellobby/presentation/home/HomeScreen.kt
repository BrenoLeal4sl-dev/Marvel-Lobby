package com.example.marvellobby.presentation.home

import com.example.marvellobby.presentation.*
import com.example.marvellobby.presentation.explore.exploreCategories
import com.example.marvellobby.data.model.*
import android.widget.*

fun ScreenRenderer.home() {
    label("YOUR NEXT DISCOVERY")
    title("Every power opens\nanother story.")
    searchField(editable=false,gap=24)
    menu("Community","People · Followers · Messages") { vm.navigate(Route("community")) }
    if(state.home.loading)add(ui.loading())
    state.home.error?.let { sectionError(it) { vm.loadHome() } }
    if(state.home.offline)body("Connection unavailable · showing saved records")
    if(state.home.items.isNotEmpty()) {
        carousel(state.home.items.distinctBy { it.type to it.id }.take(8),"featured")
    }
    label("EXPLORE THE CONNECTIONS")
    exploreCategories()
    val invite=ui.column(24).apply {
        background=ui.gradient(android.graphics.Color.parseColor("#721722"),android.graphics.Color.parseColor("#29141A"))
        isFocusable=true;setOnClickListener { vm.ask(null) }
        contentDescription=ui.translate("Ask Marvel AI")
    }
    ui.add(invite,ui.text("MARVEL AI",12,android.graphics.Color.parseColor("#FFD2CC"),true),0)
    ui.add(invite,ui.text("A question can open\na whole universe.",24,android.graphics.Color.WHITE,true),16)
    ui.add(invite,ui.text("Marvel AI connects the dots with you.",14,android.graphics.Color.parseColor("#F2D5D5")),12)
    ui.add(invite,ui.button("Ask Marvel AI",false) { vm.ask(null) },20)
    add(invite)
    val recent=state.library.filter { it.viewedAt>0 }.sortedByDescending { it.viewedAt }.take(6)
    if(recent.isNotEmpty()) {
        label("CONTINUE EXPLORING")
        carousel(recent.map { it.entity },"recent",180,240)
    }
    menu("View recently opened") { vm.navigate(Route("history")) }
}
