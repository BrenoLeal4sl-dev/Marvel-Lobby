package com.example.marvellobby.presentation

import android.animation.ValueAnimator
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import kotlin.math.sin

class ThinkingDotsView(private val ui: UiKit): LinearLayout(ui.context) {
    private var animation: ValueAnimator?=null
    private val dots=mutableListOf<View>()
    init {
        orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        setPadding(ui.dp(18),ui.dp(16),ui.dp(18),ui.dp(16))
        contentDescription=ui.translate("Thinking…")
        background=ui.shape(ui.palette.surface,22)
        repeat(3) {
            val dot=View(context).apply { background=ui.shape(ui.palette.secondary,100);importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
            dots.add(dot);addView(dot,LayoutParams(ui.dp(7),ui.dp(7)).apply { marginEnd=ui.dp(7) })
        }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if(!ValueAnimator.areAnimatorsEnabled())return
        animation=ValueAnimator.ofFloat(0f,(Math.PI*2).toFloat()).apply {
            duration=1200;repeatCount=ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val phase=animator.animatedValue as Float
                dots.forEachIndexed { index,dot ->
                    val wave=(sin(phase-index*0.9f)+1f)/2f
                    dot.translationY=-ui.dp(5)*wave;dot.alpha=0.4f+0.6f*wave
                }
            };start()
        }
    }
    override fun onDetachedFromWindow() { animation?.cancel();animation=null;super.onDetachedFromWindow() }
}
