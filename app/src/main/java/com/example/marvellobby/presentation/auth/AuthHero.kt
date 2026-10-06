package com.example.marvellobby.presentation.auth

import android.graphics.drawable.GradientDrawable
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import coil.load
import com.example.marvellobby.presentation.UiKit

/** Bundled character artwork keeps the entrance available before the first network request. */
fun authHero(ui:UiKit,register:Boolean):View=FrameLayout(ui.context).apply {
    minimumHeight=ui.dp(if(register)132 else 112)
    background=ui.gradient(ui.palette.surface,ColorUtils.blendARGB(ui.palette.surface,ui.palette.red,0.25f),24)
    clipToOutline=true
    val artwork=HeroArtwork(ui.context).apply { importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
    listOf(1455 to -12f,1443 to 9f).forEachIndexed { index,(id,angle) ->
        val panel=FrameLayout(ui.context).apply {
            background=ui.shape(ui.palette.red,14);clipToOutline=true;rotation=angle;alpha=0.85f
            addView(ImageView(ui.context).apply { scaleType=ImageView.ScaleType.CENTER_CROP;load("file:///android_asset/avatars/$id.jpg") },FrameLayout.LayoutParams(-1,-1))
        }
        artwork.addView(panel,FrameLayout.LayoutParams(ui.dp(88),ui.dp(145),Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin=ui.dp(if(index==0)55 else -8);topMargin=ui.dp(if(index==0)16 else -6) })
    }
    addView(artwork,FrameLayout.LayoutParams(ui.dp(155),-1,Gravity.END))
    addView(View(ui.context).apply {
        background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(ui.palette.surface,ColorUtils.setAlphaComponent(ui.palette.surface,240),ColorUtils.setAlphaComponent(ui.palette.surface,0)))
    },FrameLayout.LayoutParams(-1,-1))
    val copy=ui.column(18)
    ui.add(copy,ui.text("MARVEL LOBBY",10,ui.palette.secondary,true).apply { letterSpacing=0.1f },0)
    ui.add(copy,ui.text(if(register)"Your next chapter\nstarts here." else "Enter your\nMarvel universe.",21,bold=true),10)
    addView(copy,FrameLayout.LayoutParams(ui.dp(222),-2,Gravity.START or Gravity.CENTER_VERTICAL))
}

/** Portraits fill the headline's height; only readable content determines the banner size. */
private class HeroArtwork(context:Context):FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
        if(View.MeasureSpec.getMode(heightMeasureSpec)!=View.MeasureSpec.EXACTLY)setMeasuredDimension(measuredWidth,0)
    }
}
