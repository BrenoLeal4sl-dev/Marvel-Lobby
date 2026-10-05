package com.example.marvellobby.presentation

import android.view.View
import android.widget.LinearLayout
import com.example.marvellobby.data.model.*

fun ScreenRenderer.detail() {
    val detail=state.details[state.route.key] ?: DetailState(loading=true)
    if(detail.loading) { add(ui.loading());return }
    if(detail.entity==null) { sectionError(detail.error ?: "Check your connection and try again.") { vm.loadDetail() };return }
    val entity=detail.entity
    if(detail.offline)body("Connection unavailable · showing saved records")
    label(ui.translate(entity.type.resource.replace('_',' ').replaceFirstChar { it.uppercase() })+" / "+(entity.publisher?.name ?: "COMIC VINE"))
    if(entity.type!=ResourceType.POWER)add(ui.image(entity.imageUrl,220,entity.name),height=220)
    val title=ui.row()
    title.addView(ui.title(entity.name),LinearLayout.LayoutParams(0,-2,1f))
    val favorite=state.library.any { it.favorite && it.entity.id==entity.id && it.entity.type==entity.type }
    if(entity.type.canFavorite || favorite)title.addView(ui.icon("heart",if(favorite)"Remove favorite" else "Save favorite") { vm.favorite(entity) }.apply {
        isSelected=favorite
        if(favorite)background=ui.shape(ui.palette.red)
    })
    add(title)
    entity.issueLabel?.takeIf { it!=entity.name }?.let { add(ui.text(it,18,ui.palette.secondary,true)) }
    entity.realName?.takeIf { it!=entity.name }?.let { add(ui.text(it,18,ui.palette.secondary,true)) }
    entity.publisher?.let { body(ui.translate("Publisher")+" · "+it.name) }
    entity.firstIssue?.let { ref ->
        label("First appearance")
        add(ui.issueRow(ref,state.relatedIssues[state.route.key]?.records?.get(ref.id)) { vm.open(ResourceType.ISSUE,ref) },gap=10)
    }
    if(entity.type==ResourceType.CHARACTER)characterStatistics(entity)
    else if(entity.appearanceCount>0)label("${entity.appearanceCount} / ISSUE APPEARANCES")
    catalogDescription(entity)
    button("Ask Marvel AI",false) { vm.ask(entity) }
    relationships("POWERS",ResourceType.POWER,entity.powers)
    relationships("RELATED / TEAMS",ResourceType.TEAM,entity.teams)
    relationships(entity.characterRelationshipLabel,ResourceType.CHARACTER,entity.characters)
    relationships("RELATED / CHARACTERS",ResourceType.CHARACTER,entity.friends)
    relationships("RELATED / STORY ARCS",ResourceType.STORY_ARC,entity.storyArcs)
    relatedEditions(entity.issues)
}

private fun ScreenRenderer.relatedEditions(refs: List<ComicReference>) {
    if(refs.isEmpty())return
    label("ISSUES")
    val info=state.relatedIssues[state.route.key] ?: RelatedIssuesState()
    refs.take(info.count).forEach { ref ->
        add(ui.issueRow(ref,info.records[ref.id]) { vm.open(ResourceType.ISSUE,ref) },gap=10)
    }
    if(info.loading)add(ui.loading("Loading edition metadata…"))
    if(info.offline)body("Connection unavailable · showing saved records")
    if(info.error!=null) {
        body("Edition metadata could not be loaded. You can still open the records.")
        button("Try again",false) { vm.loadRelatedIssues(count=info.count) }
    }
    if(refs.size>info.count && !info.loading && info.error==null)button("Load more",false) {
        vm.loadRelatedIssues(count=info.count+20)
    }
}

private fun ScreenRenderer.relationships(title: String,type: ResourceType,refs: List<ComicReference>) {
    if(refs.isEmpty())return
    label(title)
    val countKey="relations:${state.route.key}:$title"
    val count=vm.drafts[countKey]?.toIntOrNull() ?: 8
    refs.take(count).forEach { ref ->
        if(type==ResourceType.POWER)add(ui.power(ref.name,ref.id) { vm.open(type,ref) })
        else menu(ref.displayName) { vm.open(type,ref) }
    }
    if(refs.size>count)button("Load more",false) { vm.drafts[countKey]=(count+20).toString();activity.refresh() }
}

fun ScreenRenderer.favorites() {
    label("YOUR PERSONAL ARCHIVE")
    title("Favorites")
    val legacyTypes=state.library.filter { it.favorite && !it.entity.type.canFavorite }.map { it.entity.type }.distinct()
    typeTabs(state.favoriteTab,extraTypes=legacyTypes) { it?.let(vm::favoriteTab) }
    val items=state.library.filter { it.favorite && it.entity.type==state.favoriteTab }
    if(items.isEmpty()) {
        empty("No favorites yet","Save the records you want to return to.") { vm.tab("explore") }
    } else items.forEach { item ->
        card(item.entity)
        button("Remove favorite",false) { vm.favorite(item.entity) }
    }
}

fun ScreenRenderer.history() {
    label("YOUR EXPLORATION / RECENT")
    title("Recently Viewed")
    val items=state.library.filter { it.viewedAt>0 }.sortedByDescending { it.viewedAt }
    if(items.isEmpty())empty("Nothing viewed yet","Open a record to start your exploration.") { vm.tab("explore") }
    else {
        button("Clear history",false) {
            androidx.appcompat.app.AlertDialog.Builder(activity).setTitle(ui.translate("Clear history"))
                .setMessage(ui.translate("Your favorites will remain saved."))
                .setNegativeButton(ui.translate("Cancel"),null)
                .setPositiveButton(ui.translate("Clear")) { _,_->vm.clearHistory() }.show()
        }
        items.forEach { card(it.entity) }
    }
}
