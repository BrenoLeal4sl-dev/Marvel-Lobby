package com.example.marvellobby.presentation

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.example.marvellobby.R

/** Retained across content renders so changing tabs does not replace their animations. */
class BottomNavigationBar(private val ui: UiKit,onSelect: (String)->Unit) : LinearLayout(ui.context) {
    private val font = ResourcesCompat.getFont(context,R.font.plus_jakarta_sans)!!
    private val items = linkedMapOf<String,Item>()
    private var selected: String? = null

    private inner class Item(val root: LinearLayout,val outline: View,val fill: ImageView,
                             val indicator: View,val label: android.widget.TextView) {
        var progress = 0f
        var animation: ValueAnimator? = null

        fun draw(value: Float) {
            progress = value
            outline.alpha = 1f-value
            fill.alpha = value
            indicator.alpha = value
            fill.scaleX = 1f+value*0.08f
            fill.scaleY = 1f+value*0.08f
            label.setTextColor(ColorUtils.blendARGB(ui.palette.muted,ui.palette.secondary,value))
            label.typeface = Typeface.create(font,(500+300*value).toInt(),false)
        }
    }

    init {
        orientation = HORIZONTAL
        background=ui.shape(ui.palette.surface,36,true)
        elevation=ui.dp(16).toFloat()
        setPadding(ui.dp(8),ui.dp(6),ui.dp(8),ui.dp(6))
        val destinations = listOf(
            Triple("home","Home",R.drawable.nav_home_filled),
            Triple("explore","Explore",R.drawable.nav_explore_filled),
            Triple("favorites","Favorites",R.drawable.nav_favorites_filled),
            Triple("ai","Marvel AI",R.drawable.nav_ai_filled)
        )
        destinations.forEach { (screen,title,drawable) ->
            val root = ui.column().apply {
                gravity = Gravity.CENTER
                setPadding(0,ui.dp(6),0,ui.dp(6))
                contentDescription = ui.translate(title)
                tag = "navigation:$screen"
                isFocusable = true
                setOnClickListener { onSelect(screen) }
            }
            val slot = FrameLayout(context)
            val indicator = View(context).apply { background=ui.gradient(ui.palette.red,android.graphics.Color.parseColor("#A11222"),18) }
            slot.addView(indicator,FrameLayout.LayoutParams(ui.dp(48),ui.dp(32),Gravity.CENTER))
            val outline = ui.icon(screen,title).apply {
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                (this as? ImageView)?.imageTintList = ColorStateList.valueOf(ui.palette.muted)
            }
            val fill = ImageView(context).apply {
                setImageResource(drawable)
                imageTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            slot.addView(outline,FrameLayout.LayoutParams(ui.dp(24),ui.dp(24),Gravity.CENTER))
            slot.addView(fill,FrameLayout.LayoutParams(ui.dp(24),ui.dp(24),Gravity.CENTER))
            root.addView(slot,LayoutParams(-1,ui.dp(32)))
            val label = ui.text(title,11).apply {
                gravity = Gravity.CENTER
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            ui.add(root,label,4)
            items[screen] = Item(root,outline,fill,indicator,label).also { it.draw(0f) }
            addView(root,LayoutParams(0,-1,1f))
        }
    }

    fun select(screen: String) {
        if (screen==selected) return
        val animate = selected!=null && ValueAnimator.areAnimatorsEnabled()
        selected = screen
        items.forEach { (key,item) ->
            val active = key==screen
            item.root.isSelected = active
            item.animation?.cancel()
            val target = if(active)1f else 0f
            if(animate) {
                item.animation = ValueAnimator.ofFloat(item.progress,target).apply {
                    duration = 240
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { item.draw(it.animatedValue as Float) }
                    start()
                }
            } else item.draw(target)
        }
    }

    fun stopAnimations() { items.values.forEach { it.animation?.cancel() } }
}
