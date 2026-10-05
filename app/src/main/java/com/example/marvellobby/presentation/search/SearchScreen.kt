package com.example.marvellobby.presentation.search

import com.example.marvellobby.presentation.*
import com.example.marvellobby.data.model.*
import android.widget.*

fun ScreenRenderer.search() {
     searchField()
     val page=state.pages["search"]
     if(page==null || page.query.isBlank()) {
         label("RECENT SEARCHES")
         if(state.recentSearches.isEmpty())body("Search characters, teams, powers and story arcs.")
         state.recentSearches.forEach { term -> menu(term) { vm.search(term) } }
         label("START WITH A CONNECTION")
         menu("Powers") { vm.navigate(Route("catalog",ResourceType.POWER)) }
         menu("Teams") { vm.navigate(Route("catalog",ResourceType.TEAM)) }
         return
     }
     typeTabs(state.searchType,true) { vm.searchType(it) }
     if(page.offline)body("Connection unavailable · showing saved records")
     if(page.loading)add(ui.loading())
     page.error?.let { sectionError(it) { vm.retrySearch() } }
     val results=page.items.filter { state.searchType==null || it.type==state.searchType }
     if(!page.loading && page.error==null && results.isEmpty()) { title("No results");body("Try a different name or another category.") }
     results.groupBy { it.type }.forEach { (type,items) ->
         label(typeLabel(type).uppercase());items.forEach { card(it) }
     }
 }

