package com.example.marvellobby.presentation

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout

/** Keep the outgoing screen (including its navbar) until the crossfade finishes. */
class ScreenTransitionHost(context: Context) : FrameLayout(context) {
    private class Layer(context: Context) : FrameLayout(context) {
        var inactive=false
        override fun onInterceptTouchEvent(event: MotionEvent)=inactive || super.onInterceptTouchEvent(event)
        override fun onTouchEvent(event: MotionEvent)=inactive || super.onTouchEvent(event)
    }
    private var active: Layer?=null
    private var outgoing: Layer?=null
    val transitioning get()=outgoing!=null

    fun show(view: View,sameRoute: Boolean,animate: Boolean,enteringAi: Boolean) {
        // Loading/profile changes replace content inside the animated layer, without restarting motion.
        if(sameRoute && active!=null) {
            active!!.removeAllViews();active!!.addView(view,LayoutParams(-1,-1))
            return
        }
        outgoing?.let { it.animate().withEndAction(null).cancel();removeView(it) }
        outgoing=null
        val previous=active
        previous?.animate()?.withEndAction(null)?.cancel()
        val next=Layer(context).apply { tag="screen:active";addView(view,LayoutParams(-1,-1)) }
        addView(next,LayoutParams(-1,-1));active=next
        if(!animate || previous==null || !ValueAnimator.areAnimatorsEnabled()) {
            previous?.let(::removeView)
            return
        }
        previous.tag="screen:outgoing";previous.inactive=true
        previous.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        previous.descendantFocusability=FOCUS_BLOCK_DESCENDANTS
        outgoing=previous
        val distance=(if(enteringAi)18 else -12)*resources.displayMetrics.density
        next.alpha=0f;next.translationY=distance
        val easing=PathInterpolator(0.2f,0f,0f,1f)
        previous.animate().alpha(0f).translationY(-distance*0.5f).setDuration(200).setInterpolator(easing)
            .withEndAction {
                if(outgoing===previous) { removeView(previous);outgoing=null }
            }.start()
        next.animate().alpha(1f).translationY(0f).setDuration(300).setInterpolator(easing).start()
    }

    fun dispose() {
        outgoing?.animate()?.withEndAction(null)?.cancel()
        active?.animate()?.withEndAction(null)?.cancel()
        outgoing=null;active=null;removeAllViews()
    }
}
