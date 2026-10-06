package com.example.marvellobby.presentation.social

import com.example.marvellobby.R
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.view.Gravity
import android.text.TextUtils
import com.example.marvellobby.data.model.ResourceType
import com.example.marvellobby.presentation.*

fun ScreenRenderer.favoriteSharing() {
    val social=activity.community
    val sharing=social.state.value.sharing
    label("Public favorites")
    body("Share your favorite characters, powers, teams and stories. You can hide them at any time.")
    if(social.state.value.requiresSignIn) { communitySessionRequired();return }
    if(sharing.busy)add(ui.loading("Syncing favorites…"))
    sharing.error?.let { body(it);button("Try again",false) { social.syncFavorites() } }
    sharing.enabled?.let { enabled ->
        add(ui.button(if(enabled)"Hide my favorites" else "Share my favorites",!enabled) {
            social.shareFavorites(!enabled)
        }.apply { isEnabled=!sharing.busy },height=52)
        body(if(sharing.error!=null)"Visibility could not be confirmed. Refresh to check." else if(enabled)"Your favorites are visible on your public profile." else "Your favorites are private.")
    }
}

fun ScreenRenderer.publicFavorites(userId: String) {
    val social=activity.community
    val selected=social.favoriteType(userId)
    val key="$userId:${selected.name}"
    val page=social.state.value.publicFavorites[key] ?: PublicFavoritesState()
    val heading=ui.row()
    heading.addView(ui.text("Favorites",20,bold=true),LinearLayout.LayoutParams(0,-2,1f))
    heading.addView(ui.actionIcon(R.drawable.ic_refresh,"Refresh") { social.loadFavorites(userId) }.apply { isEnabled=!page.loading })
    add(heading,gap=28)
    val filters=ui.row()
    listOf(ResourceType.CHARACTER,ResourceType.POWER,ResourceType.TEAM,ResourceType.STORY_ARC).forEach { type ->
        filters.addView(ui.button(typeLabel(type),type==selected) { social.selectFavoriteType(userId,type) }.apply {
            isSelected=type==selected;tag="public-favorites:filter:${type.name}"
        },LinearLayout.LayoutParams(-2,ui.dp(48)).apply { marginEnd=ui.dp(8) })
    }
    val filterPosition="public-favorite-filters:$userId"
    val filterX=vm.scrollPositions[filterPosition] ?: 0
    add(HorizontalScrollView(activity).apply {
        isHorizontalScrollBarEnabled=false;addView(filters);post { scrollTo(filterX,0) }
        setOnScrollChangeListener { _:View,x:Int,_:Int,_:Int,_:Int -> vm.scrollPositions[filterPosition]=x }
    },gap=12)
    if(page.loading && page.items.isEmpty())add(ui.loading(),gap=12)
    if(page.loaded && !page.visible) {
        body("This person keeps their favorites private.")
    } else if(page.loaded && page.items.isEmpty() && !page.loading && page.error==null) {
        body("No shared favorites in this category yet.")
    }
    if(page.items.isNotEmpty()) {
        val strip=ui.row()
        page.items.forEach { record ->
            val tile=FrameLayout(activity).apply {
                background=ui.shape(ui.palette.surface,24);clipToOutline=true;isFocusable=true
                contentDescription=record.name
                tag="public-favorites:${record.type.name}:${record.id}"
                setOnClickListener { vm.navigate(Route("detail",type=record.type,id=record.id,title=record.name)) }
            }
            if(record.type==ResourceType.POWER && record.imageUrl==null) {
                val artwork=FrameLayout(activity).apply { background=ui.gradient(ui.palette.raised,ui.palette.surface,0) }
                artwork.addView(android.widget.ImageView(activity).apply {
                    setImageResource(R.drawable.ic_power_bolt)
                    imageTintList=android.content.res.ColorStateList.valueOf(ui.palette.secondary)
                    importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },FrameLayout.LayoutParams(ui.dp(42),ui.dp(42),Gravity.CENTER))
                tile.addView(artwork,FrameLayout.LayoutParams(-1,ui.dp(154)))
            } else tile.addView(ui.image(record.imageUrl,154,record.name.take(2)),FrameLayout.LayoutParams(-1,ui.dp(154)))
            val copy=ui.column(14).apply { background=ui.gradient(ui.palette.surface,ui.palette.raised,0) }
            ui.add(copy,ui.text(typeLabel(record.type),11,ui.palette.secondary,true),0)
            ui.add(copy,ui.text(record.name,17,bold=true).apply { text=record.name;maxLines=2;ellipsize=TextUtils.TruncateAt.END },8)
            tile.addView(copy,FrameLayout.LayoutParams(-1,ui.dp(96),Gravity.BOTTOM))
            strip.addView(tile,LinearLayout.LayoutParams(ui.dp(168),ui.dp(250)).apply { marginEnd=ui.dp(12) })
        }
        if(page.next!=null)strip.addView(ui.button(if(page.loading)"Loading…" else "Load more",false) {
            social.loadFavorites(userId,more=true)
        }.apply { isEnabled=!page.loading },LinearLayout.LayoutParams(ui.dp(132),ui.dp(64)))
        val position="public-favorites:$key"
        val saved=vm.scrollPositions[position] ?: 0
        add(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled=false;addView(strip);tag="public-favorites:carousel"
            post { scrollTo(saved,0) }
            setOnScrollChangeListener { _:View,x:Int,_:Int,_:Int,_:Int -> vm.scrollPositions[position]=x }
        },gap=16)
    }
    if(page.loading && page.items.isNotEmpty())add(ui.loading("Loading…"),gap=12)
    page.error?.let { message ->
        body(message);button("Try again",false) { social.loadFavorites(userId,more=page.items.isNotEmpty() && page.next!=null) }
    }
}
