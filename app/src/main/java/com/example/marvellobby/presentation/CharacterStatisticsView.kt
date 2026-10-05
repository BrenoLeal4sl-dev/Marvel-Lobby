package com.example.marvellobby.presentation

import android.content.res.ColorStateList
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.example.marvellobby.data.model.*

fun ScreenRenderer.characterStatistics(entity: ComicEntity) {
    val stats=entity.characterStatistics()
    val panel=ui.column(20).apply { background=ui.gradient(ui.palette.surface,ui.palette.raised) }
    ui.add(panel,ui.text("CATALOG STATISTICS",12,ui.palette.secondary,true),0)
    stats.chunked(2).forEach { pair ->
        val row=ui.row()
        pair.forEachIndexed { index,stat ->
            val cell=ui.column()
            ui.add(cell,ui.text(stat.count?.toString() ?: "—",30,bold=true),0)
            ui.add(cell,ui.text(stat.label,12,ui.palette.muted),8)
            row.addView(cell,LinearLayout.LayoutParams(0,-2,1f).apply { if(index==0)marginEnd=ui.dp(12) })
        }
        ui.add(panel,row,20)
    }
    ui.add(panel,ui.text("Counts recorded by Comic Vine, not strength or intelligence ratings. A dash means unavailable data.",12,ui.palette.muted),18)
    add(panel)
    entity.origin?.takeIf { it.isNotBlank() }?.let {
        add(ui.text(ui.translate("Origin")+" · "+ui.translate(it),14,ui.palette.secondary,true))
    }
    entity.aliases?.split('\n')?.map { it.trim() }?.filter { it.isNotBlank() && !it.equals(entity.name,true) }
        ?.distinct()?.takeIf { it.isNotEmpty() }?.let {
            add(ui.text(ui.translate("Aliases")+" · "+it.take(8).joinToString(" · "),13,ui.palette.muted))
        }
    val base=state.comparisonCharacter
    if(base!=null && base.id!=entity.id) {
        val comparison=ui.column(20).apply { background=ui.shape(ui.palette.surface,24,true) }
        ui.add(comparison,ui.text("CHARACTER COMPARISON",12,ui.palette.secondary,true),0)
        ui.add(comparison,ui.text(base.name+" × "+entity.name,20,bold=true),12)
        val other=base.characterStatistics()
        stats.forEachIndexed { index,stat ->
            ui.add(comparison,ui.text(stat.label,13,bold=true),20)
            comparisonBar(comparison,base.name,other[index].count,stat.count,ui.palette.secondary)
            comparisonBar(comparison,entity.name,stat.count,other[index].count,ui.palette.red)
        }
        ui.add(comparison,ui.text("Bars compare these two records on the same scale for each metric. Team relationships may include former memberships.",12,ui.palette.muted),18)
        add(comparison)
        button("Clear comparison",false) { vm.clearComparison() }
    }
    button(if(base?.id==entity.id)"Choose another character" else "Compare with another character",false) { vm.compareCharacter(entity) }
}

private fun ScreenRenderer.comparisonBar(parent: LinearLayout,name: String,value: Int?,other: Int?,color: Int) {
    val line=ui.row()
    line.addView(ui.text(name,12,ui.palette.muted),LinearLayout.LayoutParams(0,-2,1f))
    line.addView(ui.text(value?.toString() ?: "—",14,color,true),LinearLayout.LayoutParams(-2,-2))
    ui.add(parent,line,10)
    val fraction=comparisonFraction(value,other)
    if(fraction==null)return
    val bar=ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal).apply {
        val track=ui.shape(ui.palette.raised,4)
        val fill=ClipDrawable(ui.shape(color,4),Gravity.START,ClipDrawable.HORIZONTAL)
        progressDrawable=LayerDrawable(arrayOf(track,fill)).apply {
            setId(0,android.R.id.background);setId(1,android.R.id.progress)
        }
        max=1000;progress=(fraction*1000).toInt()
        progressTintList=ColorStateList.valueOf(color)
        contentDescription=name+": "+value
    }
    ui.add(parent,bar,6,height=6)
}
