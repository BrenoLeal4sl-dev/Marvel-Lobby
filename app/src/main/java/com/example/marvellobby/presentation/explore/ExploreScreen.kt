package com.example.marvellobby.presentation.explore

import android.graphics.Color
import com.example.marvellobby.presentation.*
import com.example.marvellobby.data.model.*

fun ScreenRenderer.explore() {
    label("EXPLORE THE CONNECTIONS")
    title("Choose a thread.\nSee where it leads.")
    searchField(editable=false,gap=24)
    exploreCategories()
    val entities=state.home.items.distinctBy { it.type to it.id }.take(5)
    if(entities.isNotEmpty()) {
        label("CONTINUE EXPLORING")
        carousel(entities,"discover",220,280)
    }
}

fun ScreenRenderer.exploreCategories() {
    data class Category(val type: ResourceType,val name: String,val subtitle: String,val icon: String,val color: String)
    listOf(
        Category(ResourceType.CHARACTER,"Characters","The people behind the masks","profile","#A7254B"),
        Category(ResourceType.TEAM,"Teams","Alliances that change everything","home","#633EB0"),
        Category(ResourceType.POWER,"Powers","Extraordinary abilities.","explore","#146D7C"),
        Category(ResourceType.STORY_ARC,"Story Arcs","Events that redraw the universe.","ai","#A15A21")
    ).forEach { item ->
        add(ui.category(item.name,item.subtitle,item.icon,Color.parseColor(item.color)) {
            vm.navigate(Route("catalog",item.type))
        },gap=12)
    }
}
