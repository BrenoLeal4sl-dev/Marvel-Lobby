package com.example.marvellobby.presentation.catalog

import com.example.marvellobby.presentation.*
import com.example.marvellobby.data.model.*
import android.widget.*

fun ScreenRenderer.catalog() {
     val route=state.route;val page=state.pages[route.key] ?: BrowseState(loading=true)
     searchField(route.key)
     if(route.type==ResourceType.CHARACTER)state.comparisonCharacter?.let { base ->
         menu(ui.translate("Choose a character to compare with")+" "+base.name,"Clear comparison") { vm.clearComparison() }
     }
     if(route.type==ResourceType.CHARACTER) {
         val controls=ui.row()
         controls.addView(ui.button("Filters",false) { vm.navigate(Route("filters")) },LinearLayout.LayoutParams(0,ui.dp(48),1f).apply { marginEnd=ui.dp(12) })
         val sortLabel=if(page.query.isNotBlank()) "Relevance" else when(page.sort) {
             "name:asc" -> "Name A–Z"
             "name:desc" -> "Name Z–A"
             else -> "Recently updated"
         }
         controls.addView(ui.button(sortLabel,false) { vm.sort() }.apply { isEnabled=page.query.isBlank() },LinearLayout.LayoutParams(0,ui.dp(48),1f))
         add(controls)
         if(page.marvelOnly)label("Marvel Comics")
         if(page.power.isNotBlank())menu(page.power,"Remove power filter") { vm.filters(page.marvelOnly,"") }
     } else {
         label(when(route.type) { ResourceType.TEAM->"ALLIANCES / INDEX";ResourceType.POWER->"POWERS / INDEX";else->"THE STORY LIBRARY" })
         title(when(route.type) { ResourceType.TEAM->"Stronger together.\nMore complex apart.";ResourceType.POWER->"Extraordinary abilities.\nConnected stories.";else->"Events that redraw\nthe universe." })
     }
     if(page.offline)body("Connection unavailable · showing saved records")
     if(route.type==ResourceType.POWER)page.items.forEach { card(it) }
     else page.items.chunked(2).forEach { pair ->
         val row=ui.row()
         pair.forEachIndexed { index,entity ->
             val poster=ui.poster(entity,160,260) { vm.open(entity) }
             row.addView(poster,LinearLayout.LayoutParams(0,ui.dp(260),1f).apply {
                 if(index==0)marginEnd=ui.dp(10)
             })
         }
         if(pair.size==1)row.addView(android.view.View(activity),LinearLayout.LayoutParams(0,1,1f))
         add(row,gap=12)
     }
     if(page.loading)add(ui.loading(if(page.power.isNotBlank())"Checking powers in the current page…" else "Loading the archive…"))
     if(page.error!=null)sectionError(page.error) { vm.loadPage() }
     if(page.items.isEmpty() && !page.loading && page.error==null) {
         title(if(page.more)"Continue exploring" else "No results")
         body(if(page.more)"No matches in the pages checked so far. There are more records to explore." else "Try a different name or remove filters.")
     }
     if(page.more && !page.loading && page.error==null)button("Load more",false) { vm.loadPage() }
 }
fun ScreenRenderer.filters() {
     val page=state.pages[Route("catalog",ResourceType.CHARACTER).key] ?: BrowseState()
     label("REFINE THE ARCHIVE");title("Filters")
     val check=CheckBox(activity).apply { text=ui.translate("Marvel Comics only");setTextColor(ui.palette.text);isChecked=vm.drafts["filter:marvel"]?.toBoolean() ?: page.marvelOnly;minimumHeight=ui.dp(48);setOnCheckedChangeListener { _,v->vm.drafts["filter:marvel"]=v.toString() } }
     add(check)
     formField("Power name (optional)","filter:power",initial=page.power)
     body("Power filters check the records on each loaded page. Publisher filters use the publisher returned by Comic Vine.")
     button("Apply filters") { activity.hideKeyboard();vm.filters(check.isChecked,vm.drafts["filter:power"] ?: page.power) }
     button("Clear filters",false) { vm.filters(false,"") }
 }

