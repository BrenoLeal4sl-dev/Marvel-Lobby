package com.example.marvellobby.rift.render

import android.graphics.*
import com.example.marvellobby.rift.engine.CharacterDefinition

/** Original vector silhouettes: geometry, equipment and emblems distinguish every kit. */
object HeroArt {
    fun draw(c:Canvas,p:Paint,h:CharacterDefinition,stride:Float=0f) {
        fun line(x:Float,y:Float,xx:Float,yy:Float,color:Int,width:Float=5f){p.style=Paint.Style.STROKE;p.color=color;p.strokeWidth=width;p.strokeCap=Paint.Cap.ROUND;c.drawLine(x,y,xx,yy,p)}
        fun box(x:Float,y:Float,xx:Float,yy:Float,color:Int,r:Float=4f){p.style=Paint.Style.FILL;p.color=color;c.drawRoundRect(x,y,xx,yy,r,r,p)}
        fun circle(x:Float,y:Float,r:Float,color:Int){p.style=Paint.Style.FILL;p.color=color;c.drawCircle(x,y,r,p)}
        val white=0xFFF3F4FC.toInt();val dark=0xFF18243A.toInt();val red=0xFFF02A3D.toInt();val tint=h.color
        val wide=if(h.key=="hulk")20f else 12f
        if(h.key in listOf("thor","doctor-strange"))box(-wide-8,-16f,wide+8,22f,red)
        line(-6f,9f,-9f,27f+stride,dark,8f);line(6f,9f,9f,27f-stride,dark,8f)
        box(-wide,-13f,wide,12f,tint,6f)
        line(-wide,-8f,-wide-9,7f-stride,tint,if(h.key=="hulk")11f else 6f)
        line(wide,-8f,wide+9,7f+stride,tint,if(h.key=="hulk")11f else 6f)
        circle(0f,-23f,if(h.key=="hulk")13f else 11f,tint)
        when(h.key) {
            "iron-man"->{box(-7f,-29f,7f,-18f,0xFFFFE1A0.toInt());line(-6f,-24f,-2f,-24f,white,2f);line(2f,-24f,6f,-24f,white,2f);circle(0f,-3f,4f,white);circle(-21f,7f,3f,white);circle(21f,7f,3f,white)}
            "hulk"->{box(-wide,7f,wide,16f,0xFF665481.toInt());line(-7f,-28f,7f,-28f,dark,5f);line(-4f,-19f,4f,-19f,dark,2f);circle(-wide-9,8f,7f,tint);circle(wide+9,8f,7f,tint)}
            "thor"->{circle(0f,-3f,5f,dark);line(22f,6f,22f,-17f,0xFF976B41.toInt(),3f);box(14f,-24f,32f,-13f,white,2f);line(-9f,-32f,9f,-32f,white,4f)}
            "wolverine"->{box(-5f,-27f,5f,-18f,dark);line(-9f,-27f,-12f,-36f,tint,4f);line(9f,-27f,12f,-36f,tint,4f);for(i in -1..1){line(-21f+i*3,7f,-27f+i*3,-8f,white,2f);line(21f+i*3,7f,27f+i*3,-8f,white,2f)};line(-7f,-5f,7f,5f,dark,3f);line(7f,-5f,-7f,5f,dark,3f)}
            "doctor-strange"->{box(-9f,-12f,9f,13f,0xFF2859AE.toInt());circle(0f,-6f,3f,0xFF7CE8AD.toInt());line(-8f,-30f,8f,-30f,dark,4f);for(x in listOf(-23f,23f)){p.style=Paint.Style.STROKE;p.color=tint;p.strokeWidth=2f;c.drawCircle(x,3f,9f,p);c.drawCircle(x,3f,6f,p)}}
            else->{line(-7f,-27f,-3f,-22f,white,3f);line(7f,-27f,3f,-22f,white,3f);circle(0f,-4f,2f,dark);for(i in 0..3){line(0f,-4f,-7f,-10f+i*4,dark,1f);line(0f,-4f,7f,-10f+i*4,dark,1f)}}
        }
        p.style=Paint.Style.FILL
    }
}
