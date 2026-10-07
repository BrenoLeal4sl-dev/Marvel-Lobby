package com.example.marvellobby.rift.render

import android.content.Context
import android.graphics.*
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.example.marvellobby.R
import com.example.marvellobby.rift.engine.*
import kotlin.math.*

/** Provisional code-native sprites. All paints, paths and entity pools are created before play. */
class RiftGameView(context: Context,val engine: RiftEngine,val language: String,
    private val phaseChanged: (RunPhase)->Unit): View(context),Choreographer.FrameCallback {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    private val font=ResourcesCompat.getFont(context,R.font.plus_jakarta_sans)
    private val joystick=RectF();private val attack=RectF();private val melee=RectF();private val dash=RectF();private val special=RectF();private val pause=RectF()
    private val tutorialNext=RectF()
    private var aimJoyX=0f;private var aimJoyY=0f
    private var tutorialSeen=-1
    private val identity=RiftIdentityView(context)
    private var joyId=-1;private var attackId=-1;private var joyX=0f;private var joyY=0f
    private var cameraX=engine.x;private var cameraY=engine.y;private var scale=1f
    private var lastFrame=0L;private var accumulator=0f;private var running=false;private var lastPhase=engine.phase
    var averageFrameMs=0f;private set
    private var samples=0
    init { isFocusable=true;contentDescription=if(language=="pt")"Arena. Joystick à esquerda, ataque, esquiva e teia à direita." else "Arena. Joystick left, attack, dash and burst right." }
    fun startFrames() {
        if(running)return
        running=true;lastFrame=0;accumulator=0f;Choreographer.getInstance().postFrameCallback(this)
    }
    fun stopFrames() {
        running=false;Choreographer.getInstance().removeFrameCallback(this);lastFrame=0;accumulator=0f
        engine.clearInput();joyId=-1;attackId=-1;joyX=0f;joyY=0f;aimJoyX=0f;aimJoyY=0f
    }
    override fun onDetachedFromWindow() { stopFrames();super.onDetachedFromWindow() }
    override fun doFrame(frameTimeNanos: Long) {
        if(!running)return
        val elapsed=if(lastFrame==0L)0f else ((frameTimeNanos-lastFrame)/1_000_000_000f).coerceIn(0f,0.08f)
        lastFrame=frameTimeNanos
        if(elapsed>0 && engine.phase==RunPhase.PLAYING) { samples++;averageFrameMs+=(elapsed*1000-averageFrameMs)/min(samples,120) }
        accumulator+=elapsed
        var steps=0
        while(accumulator>=RiftRules.STEP && steps<5) { engine.step();accumulator-=RiftRules.STEP;steps++ }
        val smoothing=1f-exp(-elapsed*8f)
        cameraX+=(engine.x-cameraX)*smoothing;cameraY+=(engine.y-cameraY)*smoothing
        if(lastPhase!=engine.phase) { lastPhase=engine.phase;engine.clearInput();joyId=-1;attackId=-1;joyX=0f;joyY=0f;phaseChanged(lastPhase) }
        if(engine.tutorial && engine.tutorialStep!=tutorialSeen) {tutorialSeen=engine.tutorialStep;if(tutorialSeen==6)phaseChanged(RunPhase.PLAYING)}
        invalidate();Choreographer.getInstance().postFrameCallback(this)
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        val density=resources.displayMetrics.density
        scale=min(w/760f,h/1100f).coerceIn(0.65f,1.8f)
        val r=min(w*0.15f,70f*density);val bottom=h-26f*density
        joystick.set(20f*density,bottom-2*r,20f*density+2*r,bottom)
        attack.set(w-20f*density-2*r,bottom-2*r,w-20f*density,bottom)
        val b=min(w*0.075f,30f*density)
        melee.set(w-22f*density-2*b,attack.top-2*b-14*density,w-22*density,attack.top-14*density)
        dash.set(attack.left-10*density,attack.top-2*b-14*density,attack.left+2*b-10*density,attack.top-14*density)
        special.set(w/2f-b,bottom-2*b,w/2f+b,bottom)
        pause.set(w-62f*density,14f*density,w-14f*density,58f*density)
        tutorialNext.set(24*density,205*density,w-24*density,250*density)
        identity.layout(0,0,(180*density).toInt(),(48*density).toInt())
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(event.actionMasked==MotionEvent.ACTION_CANCEL) { engine.clearInput();joyId=-1;attackId=-1;joyX=0f;joyY=0f;aimJoyX=0f;aimJoyY=0f;return true }
        val i=event.actionIndex;val id=event.getPointerId(i)
        if(event.actionMasked==MotionEvent.ACTION_DOWN||event.actionMasked==MotionEvent.ACTION_POINTER_DOWN) {
            val px=event.getX(i);val py=event.getY(i)
            if(pause.contains(px,py)) { engine.pause();phaseChanged(engine.phase);return true }
            if(engine.phase!=RunPhase.PLAYING)return true
            if(engine.tutorial&&engine.tutorialStep>=4&&tutorialNext.contains(px,py)) {engine.tutorialNext();return true}
            if(engine.transition>0||engine.levelUpLeft>0)return true
            when {
                px<width*0.48f && py>height*0.55f && joyId<0 -> { joyId=id;moveJoystick(px,py) }
                attack.contains(px,py) && attackId<0 && engine.character.hasRanged -> { attackId=id;moveAim(px,py) }
                melee.contains(px,py) -> engine.melee()
                dash.contains(px,py) -> engine.dash()
                special.contains(px,py) -> engine.special()
            }
        }
        if(event.actionMasked==MotionEvent.ACTION_MOVE) {
            val joy=event.findPointerIndex(joyId);if(joy>=0)moveJoystick(event.getX(joy),event.getY(joy))
            val aim=event.findPointerIndex(attackId);if(aim>=0)moveAim(event.getX(aim),event.getY(aim))
        }
        if(event.actionMasked==MotionEvent.ACTION_UP||event.actionMasked==MotionEvent.ACTION_POINTER_UP) {
            if(id==joyId) { joyId=-1;engine.moveX=0f;engine.moveY=0f;joyX=0f;joyY=0f }
            if(id==attackId) { attackId=-1;engine.releaseWeb();aimJoyX=0f;aimJoyY=0f }
            if(event.actionMasked==MotionEvent.ACTION_UP)performClick()
        }
        return true
    }
    private fun moveJoystick(px: Float,py: Float) {
        val r=joystick.width()/2;var dx=(px-joystick.centerX())/r;var dy=(py-joystick.centerY())/r
        val length=max(1f,sqrt(dx*dx+dy*dy));dx/=length;dy/=length
        joyX=dx;joyY=dy;engine.moveX=dx;engine.moveY=dy
    }
    private fun moveAim(px: Float,py: Float) {
        val dx=(px-attack.centerX())/(attack.width()/2);val dy=(py-attack.centerY())/(attack.height()/2)
        val length=max(1f,sqrt(dx*dx+dy*dy));aimJoyX=dx/length;aimJoyY=dy/length;engine.aim(aimJoyX,aimJoyY)
    }
    override fun performClick(): Boolean { super.performClick();return true }
    private fun color(value: Int) { paint.color=value;paint.style=Paint.Style.FILL;paint.strokeWidth=2f }
    private fun circle(c: Canvas,x: Float,y: Float,r: Float,fill: Int) { color(fill);c.drawCircle(x,y,r,paint) }
    private fun line(c: Canvas,x: Float,y: Float,xx: Float,yy: Float,fill: Int,stroke: Float=2f) { color(fill);paint.strokeWidth=stroke;paint.strokeCap=Paint.Cap.ROUND;c.drawLine(x,y,xx,yy,paint) }
    private fun text(c: Canvas,value: String,x: Float,y: Float,size: Float,fill: Int=WHITE) {
        color(fill);paint.typeface=font;paint.textSize=size;c.drawText(value,x,y,paint)
    }
    override fun onDraw(c: Canvas) {
        c.drawColor(BACKGROUND)
        c.save()
        val shake=if(engine.shake>0)sin(engine.tick*2.1f)*engine.shake*10f else 0f
        c.translate(width/2f+shake,height*0.44f);c.scale(scale,scale);c.translate(-cameraX,-cameraY)
        drawArena(c)
        if(engine.aiming) {
            val range=engine.aimRange
            line(c,engine.x,engine.y,engine.x+engine.aimX*range,engine.y+engine.aimY*range,0x22F3F4FC,18f)
            line(c,engine.x,engine.y,engine.x+engine.aimX*range,engine.y+engine.aimY*range,if(engine.webCharges>0)0x99F3F4FC.toInt()else RED,2f)
            for(i in 1..8)if(i*72<range)circle(c,engine.x+engine.aimX*i*72f,engine.y+engine.aimY*i*72f,3f,0xBBFFFFFF.toInt())
        }
        for(orb in engine.orbs)if(orb.alive) { circle(c,orb.x,orb.y,7f,0x334AEBC7);circle(c,orb.x,orb.y,3.5f,0xFF58E6CB.toInt()) }
        for(e in engine.enemies)if(e.alive)drawEnemy(c,e)
        for(p in engine.projectiles)if(p.alive) {
            val tint=if(p.friendly)WHITE else ORANGE
            line(c,p.x,p.y,p.x-p.vx*0.02f,p.y-p.vy*0.02f,tint,if(p.friendly)3f else 5f)
            if(p.friendly) { line(c,p.x-5,p.y-3,p.x+5,p.y+3,WHITE);line(c,p.x-3,p.y+5,p.x+3,p.y-5,WHITE) }
        }
        if(engine.specialFlash>0) {
            color(0x88EAE5FF.toInt());paint.style=Paint.Style.STROKE;paint.strokeWidth=3f
            val r=(1f-engine.specialFlash/0.5f)*(220f+engine.rank(Upgrade.SPECIAL)*35)
            c.drawCircle(engine.x,engine.y,r,paint)
            for(i in 0..11) { val angle=i*PI.toFloat()/6;line(c,engine.x+cos(angle)*r*0.6f,engine.y+sin(angle)*r*0.6f,engine.x+cos(angle)*r,engine.y+sin(angle)*r,0x88FFFFFF.toInt()) }
        }
        drawHero(c,engine.x,engine.y,engine.animation,engine.time,engine.facingX,engine.facingY,engine.hurt)
        if(engine.explosionFlash>0) {
            color(0xCCFFFFFF.toInt());paint.style=Paint.Style.STROKE;paint.strokeWidth=3f
            c.drawCircle(engine.explosionX,engine.explosionY,90f*(1f-engine.explosionFlash/.35f),paint)
            for(i in 0..7){val a=i*PI.toFloat()/4;line(c,engine.explosionX,engine.explosionY,engine.explosionX+cos(a)*90,engine.explosionY+sin(a)*90,0x55FFFFFF)}
        }
        for(effect in engine.effects)if(effect.life>0) {
            circle(c,effect.x,effect.y,4f+(1f-effect.life/.55f)*15f,if(effect.web)0x44FFFFFF else 0x55FFB56B)
            text(c,effect.damage.toString(),effect.x-8,effect.y-25-(.55f-effect.life)*35,14f,if(effect.web)WHITE else ORANGE)
        }
        if(engine.meleeFlash>0) {
            color(0xCCFFB56B.toInt());paint.style=Paint.Style.STROKE;paint.strokeWidth=7f
            val angle=atan2(engine.facingY,engine.facingX)*180f/PI.toFloat()
            c.drawArc(engine.x-75,engine.y-75,engine.x+75,engine.y+75,angle-70,140f,false,paint)
        }
        c.restore()
        hud(c);controls(c)
        if(engine.levelUpLeft>0) {
            color(0x99121A29.toInt());c.drawRect(0f,height*.3f,width.toFloat(),height*.3f+70*resources.displayMetrics.density,paint)
            text(c,if(language=="pt")"SUBIU DE NÍVEL · LV ${engine.level}" else "LEVEL UP · LV ${engine.level}",24*resources.displayMetrics.density,height*.3f+42*resources.displayMetrics.density,20*resources.displayMetrics.density)
        }
        if(engine.tutorial)drawTutorial(c)
        if(engine.transition>0) {
            val d=resources.displayMetrics.density
            color(0xCC090E17.toInt());c.drawRoundRect(16*d,height*.3f,width-16*d,height*.3f+90*d,20*d,20*d,paint)
            val title=when(engine.transitionKind){"boss"->if(language=="pt")"ALERTA · SENTINELA" else "WARNING · SENTINEL";"wave"->if(language=="pt")"ONDA ${engine.wave-1} CONCLUÍDA" else "WAVE ${engine.wave-1} COMPLETE";"upgrade"->if(language=="pt")"MELHORIA ADQUIRIDA" else "UPGRADE ACQUIRED";else->if(language=="pt")"SOBREVIVA À FENDA" else "SURVIVE THE RIFT"}
            text(c,title,32*d,height*.3f+32*d,14*d)
            text(c,when(engine.transitionKind){"wave"->"${if(language=="pt")"ONDA" else "WAVE"} ${engine.wave} · ${ceil(engine.transition).toInt()}";"upgrade"->Upgrade.valueOf(engine.feedback).displayName(language);"boss"->if(language=="pt")"Prepare-se para o combate" else "Prepare for combat";else->if(language=="pt")"Sobreviva. Evolua. Supere seu recorde." else "Survive. Evolve. Beat your record."},32*d,height*.3f+62*d,12*d)
        }
        if(engine.bossBanner>0 && engine.transition<=0) {
            color(0xCC551126.toInt());c.drawRect(0f,height*0.3f,width.toFloat(),height*0.3f+68*resources.displayMetrics.density,paint)
            text(c,if(language=="pt")"FENDA INSTÁVEL · SENTINELA" else "RIFT UNSTABLE · SENTINEL",width*0.08f,height*0.3f+40*resources.displayMetrics.density,15*resources.displayMetrics.density)
        }
    }
    private fun drawArena(c: Canvas) {
        color(0xFF111723.toInt());c.drawRect(0f,0f,RiftRules.WORLD,RiftRules.WORLD,paint)
        val left=((cameraX-width/scale/2)/80).toInt().coerceAtLeast(0)*80
        val top=((cameraY-height/scale/2)/80).toInt().coerceAtLeast(0)*80
        var xx=left.toFloat();while(xx<min(RiftRules.WORLD,cameraX+width/scale)) { line(c,xx,0f,xx,RiftRules.WORLD,0xFF1C2738.toInt());xx+=80f }
        var yy=top.toFloat();while(yy<min(RiftRules.WORLD,cameraY+height/scale)) { line(c,0f,yy,RiftRules.WORLD,yy,0xFF1C2738.toInt());yy+=80f }
        color(RED);paint.style=Paint.Style.STROKE;paint.strokeWidth=7f;c.drawRect(0f,0f,RiftRules.WORLD,RiftRules.WORLD,paint)
        for(o in RiftRules.obstacles) {
            color(0xFF070C14.toInt());c.drawRoundRect(o.x+5,o.y+8,o.x+o.w+5,o.y+o.h+8,8f,8f,paint)
            color(0xFF263343.toInt());c.drawRoundRect(o.x,o.y,o.x+o.w,o.y+o.h,8f,8f,paint)
            line(c,o.x+8,o.y+5,o.x+o.w-8,o.y+5,0xFF50667A.toInt(),3f)
        }
        // Fixed central rift geometry, independent of random gameplay state.
        color(0xFF4D2338.toInt());paint.style=Paint.Style.STROKE;paint.strokeWidth=4f
        c.drawCircle(900f,900f,85f,paint);c.drawCircle(900f,900f,74f,paint)
    }
    private fun drawEnemy(c: Canvas,e: Enemy) {
        c.save();c.translate(e.x,e.y)
        circle(c,0f,5f,e.radius,0x66000000)
        val tint=if(e.flash>0)WHITE else when(e.kind) { EnemyKind.MELEE->0xFFBF647A.toInt();EnemyKind.FAST->0xFFDEB356.toInt();EnemyKind.RANGED->0xFF69ADAC.toInt();EnemyKind.TANK->0xFF8A87BE.toInt();EnemyKind.BOSS->RED }
        when(e.kind) {
            EnemyKind.FAST -> { path.reset();path.moveTo(0f,-18f);path.lineTo(15f,12f);path.lineTo(0f,6f);path.lineTo(-15f,12f);path.close();color(tint);c.drawPath(path,paint) }
            EnemyKind.RANGED -> { color(tint);c.drawRoundRect(-16f,-16f,16f,16f,6f,6f,paint);circle(c,0f,0f,7f,BACKGROUND);line(c,0f,0f,(engine.x-e.x)*0.07f,(engine.y-e.y)*0.07f,tint,4f) }
            EnemyKind.TANK -> { color(tint);c.drawRoundRect(-24f,-24f,24f,24f,5f,5f,paint);line(c,-13f,-5f,13f,-5f,BACKGROUND,7f);line(c,-13f,8f,13f,8f,BACKGROUND,7f) }
            EnemyKind.BOSS -> {
                circle(c,0f,0f,46f,tint);circle(c,0f,0f,31f,BACKGROUND)
                for(i in 0..5) { val a=i*PI.toFloat()/3+engine.time*0.7f;line(c,cos(a)*18,sin(a)*18,cos(a)*52,sin(a)*52,tint,9f) }
                circle(c,0f,0f,14f,WHITE)
            }
            EnemyKind.MELEE -> { circle(c,0f,0f,16f,tint);line(c,-19f,6f,-10f,-2f,tint,5f);line(c,19f,6f,10f,-2f,tint,5f);line(c,-6f,-4f,6f,-4f,BACKGROUND,4f) }
        }
        if(e.elite) { color(ORANGE);paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;c.drawCircle(0f,0f,e.radius+5,paint) }
        if(e.slow>0) {
            color(0xCCF3F4FC.toInt());paint.style=Paint.Style.STROKE;paint.strokeWidth=1.5f;c.drawCircle(0f,0f,e.radius+3,paint)
            for(i in -1..1){line(c,-e.radius,i*8f,e.radius,-i*8f,0x99F3F4FC.toInt());line(c,i*8f,-e.radius,-i*8f,e.radius,0x99F3F4FC.toInt())}
        }
        if(e.hp<e.maxHp) { color(0xFF352C36.toInt());c.drawRect(-e.radius,-e.radius-10,e.radius,-e.radius-6,paint);color(tint);c.drawRect(-e.radius,-e.radius-10,-e.radius+e.radius*2*e.hp/e.maxHp,-e.radius-6,paint) }
        c.restore()
        if(e.windup>0) {
            color(0x44F02A3D);c.drawCircle(if(e.pattern%2==0)e.x else e.aimX,if(e.pattern%2==0)e.y else e.aimY,if(e.pattern%2==0)100f else 150f,paint)
            color(RED);paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;c.drawCircle(if(e.pattern%2==0)e.x else e.aimX,if(e.pattern%2==0)e.y else e.aimY,if(e.pattern%2==0)100f else 150f,paint)
        }
    }
    private fun drawHero(c: Canvas,x: Float,y: Float,state: AnimationState,time: Float,fx: Float,fy: Float,hurt: Float) {
        c.save();c.translate(x,y)
        if(state==AnimationState.DEATH)c.rotate(80f*engine.deathProgress)
        // Upright three-quarter silhouette; feet stay grounded instead of rotating the entire body.
        val stride=if(state==AnimationState.RUN)sin(time*19f)*3f else 0f
        val tint=if(hurt>0 && (time*18).toInt()%2==0)WHITE else RED
        color(0x66000000);c.drawOval(-17f,17f,17f,30f,paint)
        if(state==AnimationState.RUN)c.translate(0f,-abs(stride)*0.4f)
        if(state==AnimationState.DASH) { line(c,-8f-fx*25f,8f-fy*25f,-8f-fx*65f,8f-fy*65f,0x77E4E8FF,5f);line(c,8f-fx*25f,8f-fy*25f,8f-fx*65f,8f-fy*65f,0x77E4E8FF,5f) }
        line(c,-6f,6f,-9f,23f+stride,BLUE,7f);line(c,6f,6f,9f,23f-stride,BLUE,7f)
        line(c,-9f,23f+stride,-11f,28f+stride,tint,6f);line(c,9f,23f-stride,11f,28f-stride,tint,6f)
        color(BLUE);c.drawRoundRect(-12f,-10f,12f,12f,7f,7f,paint)
        color(tint);c.drawRoundRect(-8f,-12f,8f,7f,5f,5f,paint)
        val reaching=if(state==AnimationState.ATTACK||state==AnimationState.SPECIAL)15f else 0f
        line(c,-10f,-8f,-20f,-2f-stride-reaching,tint,6f);line(c,10f,-8f,20f,-2f+stride-reaching,tint,6f)
        circle(c,0f,-20f,11f,tint)
        line(c,-7f,-24f,-3f,-19f,WHITE,4f);line(c,7f,-24f,3f,-19f,WHITE,4f)
        circle(c,0f,-3f,2.5f,BACKGROUND);line(c,0f,-8f,0f,4f,BACKGROUND,2f)
        for(i in 0..3){val yy=-7f+i*3;line(c,0f,-3f,-5f,yy,BACKGROUND,1.5f);line(c,-5f,yy,-7f,yy+3,BACKGROUND,1.5f);line(c,0f,-3f,5f,yy,BACKGROUND,1.5f);line(c,5f,yy,7f,yy+3,BACKGROUND,1.5f)}
        line(c,fx*14f,fy*14f,fx*23f,fy*23f,0x99FFFFFF.toInt(),2f)
        c.restore()
    }
    private fun hud(c: Canvas) {
        val d=resources.displayMetrics.density
        color(0xDD0A101B.toInt());c.drawRoundRect(12*d,12*d,width-74*d,91*d,16*d,16*d,paint)
        text(c,"HP ${engine.hp.toInt()} / ${engine.maxHp.toInt()}",24*d,34*d,12*d)
        color(0xFF392030.toInt());c.drawRoundRect(24*d,42*d,width-90*d,49*d,3*d,3*d,paint)
        color(RED);c.drawRoundRect(24*d,42*d,24*d+(width-114*d)*engine.hp/engine.maxHp,49*d,3*d,3*d,paint)
        text(c,"LV ${engine.level} · ${if(language=="pt")"ONDA" else "WAVE"} ${engine.wave} · XP ${engine.xp}/${engine.xpGoal}",24*d,72*d,11*d)
        color(0xFF223C39.toInt());c.drawRect(24*d,81*d,width-90*d,84*d,paint)
        text(c,"${engine.score} · ${engine.time.toInt()/60}:${(engine.time.toInt()%60).toString().padStart(2,'0')}",width-170*d,34*d,11*d)
        color(0xFF58E6CB.toInt());c.drawRect(24*d,81*d,24*d+(width-114*d)*engine.xp/engine.xpGoal,84*d,paint)
        color(0xDD222B3A.toInt());c.drawRoundRect(pause,14*d,14*d,paint)
        line(c,pause.centerX()-4*d,pause.centerY()-7*d,pause.centerX()-4*d,pause.centerY()+7*d,WHITE,3*d)
        line(c,pause.centerX()+4*d,pause.centerY()-7*d,pause.centerX()+4*d,pause.centerY()+7*d,WHITE,3*d)
        val boss=engine.enemies.firstOrNull { it.alive&&it.kind==EnemyKind.BOSS }
        if(boss!=null) { text(c,"RIFT SENTINEL",24*d,114*d,11*d,ORANGE);color(0xFF3A2030.toInt());c.drawRect(24*d,121*d,width-24*d,127*d,paint);color(RED);c.drawRect(24*d,121*d,24*d+(width-48*d)*boss.hp/boss.maxHp,127*d,paint) }
        if(engine.combo>1)text(c,"×${engine.combo}",24*d,151*d,18*d,ORANGE)
    }
    private fun controls(c: Canvas) {
        val d=resources.displayMetrics.density;val r=joystick.width()/2
        circle(c,joystick.centerX(),joystick.centerY(),r,0x99425366.toInt())
        color(0x55CED5E8);paint.style=Paint.Style.STROKE;paint.strokeWidth=1*d;c.drawCircle(joystick.centerX(),joystick.centerY(),r,paint)
        circle(c,joystick.centerX()+joyX*r*0.6f,joystick.centerY()+joyY*r*0.6f,r*0.36f,0xCCD6DFEE.toInt())
        if(engine.character.hasRanged) {
            control(c,attack,if(language=="pt")"MIRA" else "AIM",0f,0f,engine.aiming)
            circle(c,attack.centerX()+aimJoyX*r*.6f,attack.centerY()+aimJoyY*r*.6f,r*.3f,0x99F3F4FC.toInt())
            for(i in 0..2){val xx=attack.centerX()+(i-1)*18*d;val yy=attack.top-11*d
                circle(c,xx,yy,5*d,if(i<engine.webCharges)WHITE else 0xFF3A4252.toInt())
                if(i==engine.webCharges){color(RED);paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d;c.drawArc(xx-5*d,yy-5*d,xx+5*d,yy+5*d,-90f,360f*engine.recharge/engine.rechargeDuration,false,paint)}
            }
        }
        control(c,melee,if(language=="pt")"GOLPE" else "MELEE",engine.meleeCooldown,.48f,engine.meleeFlash>0)
        control(c,dash,if(language=="pt")"ESQUIVA" else "DASH",engine.dashCooldown,engine.dashDuration,engine.dashLeft>0)
        control(c,special,if(language=="pt")"EXPLOSÃO" else "BURST",engine.specialCooldown,engine.specialDuration,engine.specialFlash>0)
    }
    private fun drawTutorial(c: Canvas) {
        val d=resources.displayMetrics.density;val step=engine.tutorialStep
        c.save();c.translate(20*d,95*d);identity.draw(c);c.restore()
        val titles=if(language=="pt")arrayOf("MOVIMENTO","MIRE E ATAQUE","ATAQUE CORPO A CORPO","HABILIDADE","GANHE XP","PRONTO PARA A FENDA")else arrayOf("MOVEMENT","AIM AND ATTACK","MELEE ATTACK","SPECIAL ABILITY","EARN XP","READY FOR THE RIFT")
        val lines=if(language=="pt")arrayOf("Mova o joystick esquerdo.","Arraste o direito. Solte para lançar teia.","Toque em GOLPE para atacar de perto.","Toque em EXPLOSÃO para prender inimigos.","Elimine inimigos. XP dá níveis e melhorias.","Sobreviva. Evolua. Supere seu recorde.")else arrayOf("Move the left stick.","Drag the right stick. Release to fire.","Tap MELEE to strike nearby enemies.","Tap BURST to trap nearby enemies.","Defeat enemies. XP gives levels and upgrades.","Survive. Evolve. Beat your record.")
        if(step>5)return
        color(0xEE111723.toInt());c.drawRoundRect(16*d,145*d,width-16*d,if(step>=4)260*d else 205*d,18*d,18*d,paint)
        text(c,"${step+1}/6 · ${titles[step]}",28*d,169*d,13*d)
        text(c,lines[step],28*d,193*d,11*d)
        val target=when(step){0->joystick;1->attack;2->melee;3->special;else->null}
        target?.let {color(ORANGE);paint.style=Paint.Style.STROKE;paint.strokeWidth=3*d;c.drawOval(it,paint)}
        if(step==4){color(ORANGE);paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d;c.drawRoundRect(20*d,77*d,width-86*d,88*d,3*d,3*d,paint)}
        if(step>=4){color(RED);c.drawRoundRect(tutorialNext,12*d,12*d,paint);text(c,if(language=="pt")"CONTINUAR" else "CONTINUE",36*d,234*d,13*d)}
    }
    private fun control(c: Canvas,rect: RectF,label: String,cooldown: Float,total: Float,pressed: Boolean) {
        color(if(pressed)0xFFEE6671.toInt() else if(cooldown>0)0xCC242C3A.toInt() else 0xDD961B30.toInt());c.drawOval(rect,paint)
        if(cooldown>0) { color(0xFF73839B.toInt());paint.style=Paint.Style.STROKE;paint.strokeWidth=3*resources.displayMetrics.density;c.drawArc(rect,-90f,360f*cooldown/total,false,paint) }
        paint.typeface=font;paint.textSize=9*resources.displayMetrics.density
        text(c,label,rect.centerX()-paint.measureText(label)/2,rect.centerY()+4*resources.displayMetrics.density,paint.textSize)
        if(cooldown>0)text(c,ceil(cooldown).toInt().toString(),rect.centerX()-4*resources.displayMetrics.density,rect.centerY()-10*resources.displayMetrics.density,11*resources.displayMetrics.density)
    }
    companion object {
        private const val BACKGROUND=0xFF090E17.toInt();private const val RED=0xFFF02A3D.toInt()
        private const val BLUE=0xFF2859AE.toInt();private const val WHITE=0xFFF3F4FC.toInt();private const val ORANGE=0xFFFFB56B.toInt()
    }
}
