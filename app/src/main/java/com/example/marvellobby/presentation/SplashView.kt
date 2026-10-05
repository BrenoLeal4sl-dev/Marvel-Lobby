package com.example.marvellobby.presentation

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout

/** Animations belong to the visible view and stop as soon as it leaves the screen. */
class SplashView(ui: UiKit) : LinearLayout(ui.context) {
    private val logo = ui.brandLogo(164)
    private val loading = ui.text("Carregando.",14,ui.palette.muted).apply {
        gravity = Gravity.CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var pulse: ObjectAnimator? = null
    private var dots: ValueAnimator? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(ui.dp(32),0,ui.dp(32),0)
        contentDescription = "Marvel Lobby, carregando"
        addView(logo)
        ui.add(this,loading,24)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            loading.text = "Carregando..."
            return
        }
        pulse = ObjectAnimator.ofFloat(logo,ALPHA,1f,0.45f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        dots = ValueAnimator.ofFloat(0f,3f).apply {
            duration = 1350
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                val count = (it.animatedValue as Float).toInt().coerceAtMost(2) + 1
                loading.text = "Carregando" + ".".repeat(count)
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        pulse?.cancel()
        dots?.cancel()
        pulse = null
        dots = null
        super.onDetachedFromWindow()
    }
}
