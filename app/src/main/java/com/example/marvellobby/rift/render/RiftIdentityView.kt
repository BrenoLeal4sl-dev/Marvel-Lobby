package com.example.marvellobby.rift.render

import android.content.Context
import android.graphics.*
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.example.marvellobby.R

/** Vector composition scales from Lobby cards to the Arena without loading bitmap assets. */
class RiftIdentityView(context: Context): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    private val face=ResourcesCompat.getFont(context,R.font.plus_jakarta_sans)
    init {contentDescription="Rift Arena";importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES}
    override fun onDraw(c: Canvas) {
        val s=minOf(width/340f,height/92f);c.save();c.translate((width-340*s)/2,(height-92*s)/2);c.scale(s,s)
        paint.shader=LinearGradient(0f,0f,340f,92f,intArrayOf(0xFFFF384A.toInt(),0xFF921527.toInt()),null,Shader.TileMode.CLAMP)
        path.reset();path.moveTo(8f,16f);path.lineTo(142f,0f);path.lineTo(118f,33f);path.lineTo(161f,28f);path.lineTo(129f,87f);path.lineTo(16f,73f);path.close();c.drawPath(path,paint)
        paint.shader=null;paint.color=Color.WHITE;paint.typeface=Typeface.create(face,Typeface.BOLD_ITALIC);paint.textSize=47f;c.drawText("RIFT",20f,56f,paint)
        paint.textSize=28f;c.drawText("ARENA",171f,59f,paint)
        paint.color=0xFFFF384A.toInt();paint.strokeWidth=3f;c.drawLine(174f,70f,320f,70f,paint)
        paint.color=0xFF75DCE8.toInt();path.reset();path.moveTo(165f,3f);path.lineTo(146f,39f);path.lineTo(164f,35f);path.lineTo(141f,89f);path.lineTo(185f,29f);path.lineTo(166f,33f);path.lineTo(190f,0f);path.close();c.drawPath(path,paint)
        c.restore()
    }
}
