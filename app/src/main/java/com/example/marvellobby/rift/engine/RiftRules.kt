package com.example.marvellobby.rift.engine

import kotlin.math.*

enum class RunPhase { PLAYING, PAUSED, UPGRADE, DYING, FINISHED }
enum class AnimationState { IDLE, RUN, ATTACK, DASH, HIT, DEATH, SPECIAL }
enum class EnemyKind { MELEE, RANGED, TANK, FAST, BOSS }
enum class Rarity { COMMON, RARE, EPIC, LEGENDARY }
enum class Upgrade(val rarity: Rarity,val cap: Int,val title: String,val description: String) {
    DAMAGE(Rarity.COMMON,4,"Web Shot","+25% web damage / +25% dano de teia"),
    SPEED(Rarity.COMMON,3,"Spider Sense","+12% movement / +12% movimento"),
    ATTACK_SPEED(Rarity.RARE,3,"Quick Hands","Faster shots / Disparos mais rápidos"),
    HEALTH(Rarity.COMMON,3,"Second Wind","+25 health and heal / +25 vida e cura"),
    ARMOR(Rarity.RARE,3,"Resilient","Reduce incoming damage / Reduz o dano recebido"),
    DASH(Rarity.RARE,3,"Air Step","Shorter dash cooldown / Menor intervalo da esquiva"),
    CRITICAL(Rarity.RARE,3,"Perfect Timing","+12% critical chance / +12% chance crítica"),
    PIERCE(Rarity.EPIC,2,"Thread the Needle","Webs pierce another target / Teias atravessam outro alvo"),
    RICOCHET(Rarity.EPIC,1,"Ricochet Web","One web branches to a nearby enemy / Uma teia salta para outro inimigo"),
    EXPLOSION(Rarity.LEGENDARY,1,"Web Explosion","Final hit blasts nearby enemies / Último impacto explode em área"),
    SLOW(Rarity.RARE,1,"Sticky Web","Web shots slow targets / Teias desaceleram os alvos"),
    SPECIAL(Rarity.EPIC,3,"Web Storm","Wider burst, shorter cooldown / Explosão maior, menor intervalo")
}

data class CharacterDefinition(val key: String,val catalogId: Int,val name: String,val speed: Float,val health: Float,
    val webDamage: Float,val shotCooldown: Float,val dashCooldown: Float,val specialCooldown: Float)
object RiftCharacters {
    val spider=CharacterDefinition("spider-man",1443,"Spider-Man",210f,100f,23f,0.3f,2.8f,12f)
    val all=listOf(spider)
    fun get(key: String)=all.firstOrNull { it.key==key } ?: spider
}
data class Obstacle(val x: Float,val y: Float,val w: Float,val h: Float)
object RiftRules {
    const val VERSION="rift-1"
    const val STEP=1f/60f
    const val WORLD=1800f
    const val LIMIT_SECONDS=600f
    const val MAX_ENEMIES=96
    const val MAX_PROJECTILES=192
    val obstacles=listOf(Obstacle(560f,650f,100f,210f),Obstacle(1150f,990f,100f,210f),
        Obstacle(820f,430f,210f,80f),Obstacle(820f,1310f,210f,80f))
    fun score(seconds: Float,kills: Int,elites: Int,bosses: Int,damage: Float,combo: Int): Int =
        (seconds.toInt()*10+kills*40+elites*150+bosses*1200+min(damage,200000f).toInt()/5+min(combo,200)*20)
}

/** Fixed portable PRNG; independent streams keep scheduled spawns separate from combat/upgrades. */
class RiftRandom(seed: Int) {
    private var state=if(seed==0)0x13579BDF else seed
    fun next(): Int { var x=state;x=x xor (x shl 13);x=x xor (x ushr 17);x=x xor (x shl 5);state=x;return x and Int.MAX_VALUE }
    fun unit()=next().toFloat()/Int.MAX_VALUE.toFloat()
    fun index(size: Int)=next()%size
}

class Enemy {
    var alive=false;var serial=0;var kind=EnemyKind.MELEE;var elite=false
    var x=0f;var y=0f;var hp=0f;var maxHp=0f;var radius=16f;var speed=85f;var attack=0f
    var slow=0f;var flash=0f;var windup=0f;var pattern=0;var aimX=0f;var aimY=0f
}
class Projectile {
    var alive=false;var friendly=true;var x=0f;var y=0f;var vx=0f;var vy=0f;var life=0f;var damage=0f
    var pierce=0;var branch=false;var lastHit=-1
}
class XpOrb { var alive=false;var x=0f;var y=0f;var value=0 }
data class RunResult(val seed: Int,val character: String,val duration: Float,val kills: Int,val elites: Int,
    val bosses: Int,val damage: Float,val maxCombo: Int,val level: Int,val score: Int,val extracted: Boolean,
    val upgrades: Map<String,Int>,val version: String=RiftRules.VERSION)

/** Mutable pools are owned exclusively by the game thread (Android's UI thread in this slice). */
class RiftEngine(val seed: Int,val character: CharacterDefinition=RiftCharacters.spider) {
    var phase=RunPhase.PLAYING;private set
    var x=900f;private set;var y=900f;private set
    var facingX=0f;private set;var facingY=-1f;private set
    var hp=character.health;private set;var maxHp=character.health;private set
    var time=0f;private set;var tick=0;private set;var level=1;private set;var xp=0;private set
    var kills=0;private set;var elites=0;private set;var bosses=0;private set;var damage=0f;private set
    var combo=0;private set;var maxCombo=0;private set;private var comboTimer=0f
    var dashCooldown=0f;private set;var specialCooldown=0f;private set
    var dashLeft=0f;private set;var hurt=0f;private set;var shake=0f;private set
    var specialFlash=0f;private set;var attackFlash=0f;private set;var bossBanner=0f;private set
    var choices: List<Upgrade> = emptyList();private set
    val upgrades=IntArray(Upgrade.entries.size)
    val enemies=Array(RiftRules.MAX_ENEMIES) { Enemy() }
    val projectiles=Array(RiftRules.MAX_PROJECTILES) { Projectile() }
    val orbs=Array(128) { XpOrb() }
    private val spawnRandom=RiftRandom(seed);private val combatRandom=RiftRandom(seed xor 0x546A17)
    private val upgradeRandom=RiftRandom(seed xor 0x733CA9)
    private var spawnTimer=1f;private var serial=0;private var bossCount=0;private var attackCooldown=0f;private var dying=0f
    var moveX=0f;var moveY=0f;var attacking=false
    val xpGoal get()=12+(level-1)*8
    val score get()=RiftRules.score(time,kills,elites,bosses,damage,maxCombo)
    val dashDuration get()=character.dashCooldown*0.8f.pow(rank(Upgrade.DASH))
    val specialDuration get()=character.specialCooldown*0.85f.pow(rank(Upgrade.SPECIAL))
    val animation get()=when { phase==RunPhase.DYING||phase==RunPhase.FINISHED->AnimationState.DEATH;dashLeft>0->AnimationState.DASH;
        hurt>0.45f->AnimationState.HIT;specialFlash>0->AnimationState.SPECIAL;attackFlash>0->AnimationState.ATTACK;
        moveX*moveX+moveY*moveY>0.01f->AnimationState.RUN;else->AnimationState.IDLE }
    fun rank(u: Upgrade)=upgrades[u.ordinal]
    fun pause() { if(phase==RunPhase.PLAYING)phase=RunPhase.PAUSED;clearInput() }
    fun resume() { if(phase==RunPhase.PAUSED)phase=RunPhase.PLAYING }
    fun clearInput() { moveX=0f;moveY=0f;attacking=false }
    fun dash(): Boolean {
        if(phase!=RunPhase.PLAYING||dashCooldown>0)return false
        val length=sqrt(moveX*moveX+moveY*moveY)
        if(length>0.1f) { facingX=moveX/length;facingY=moveY/length }
        dashLeft=0.18f;dashCooldown=dashDuration;return true
    }
    fun special(): Boolean {
        if(phase!=RunPhase.PLAYING||specialCooldown>0)return false
        specialCooldown=specialDuration;specialFlash=0.5f;shake=0.25f
        val radius=220f+rank(Upgrade.SPECIAL)*35f
        enemies.forEach { if(it.alive && distance2(x,y,it.x,it.y)<radius*radius) { hit(it,70f+rank(Upgrade.SPECIAL)*20f);it.slow=3f } }
        return true
    }
    fun choose(upgrade: Upgrade): Boolean {
        if(phase!=RunPhase.UPGRADE||upgrade !in choices||rank(upgrade)>=upgrade.cap)return false
        upgrades[upgrade.ordinal]++
        if(upgrade==Upgrade.HEALTH) { maxHp+=25f;hp=min(maxHp,hp+40f) }
        choices=emptyList();phase=RunPhase.PLAYING;levelCheck();return true
    }
    fun step() {
        val dt=RiftRules.STEP
        if(phase==RunPhase.DYING) { dying+=dt;if(dying>=0.8f)phase=RunPhase.FINISHED;return }
        if(phase!=RunPhase.PLAYING)return
        tick++;time=tick*dt
        if(time>=RiftRules.LIMIT_SECONDS) { phase=RunPhase.FINISHED;clearInput();return }
        dashCooldown=max(0f,dashCooldown-dt);specialCooldown=max(0f,specialCooldown-dt)
        attackCooldown=max(0f,attackCooldown-dt);hurt=max(0f,hurt-dt);shake=max(0f,shake-dt)
        attackFlash=max(0f,attackFlash-dt);specialFlash=max(0f,specialFlash-dt);bossBanner=max(0f,bossBanner-dt)
        comboTimer-=dt;if(comboTimer<=0)combo=0
        val length=sqrt(moveX*moveX+moveY*moveY)
        val mx=moveX/max(1f,length);val my=moveY/max(1f,length)
        if(length>0.1f && dashLeft<=0) { facingX=mx/max(0.001f,sqrt(mx*mx+my*my));facingY=my/max(0.001f,sqrt(mx*mx+my*my)) }
        val speed=character.speed*(1f+rank(Upgrade.SPEED)*0.12f)
        if(dashLeft>0) { movePlayer(facingX*speed*3.8f*dt,facingY*speed*3.8f*dt);dashLeft=max(0f,dashLeft-dt) }
        else movePlayer(mx*speed*dt,my*speed*dt)
        if(attacking && attackCooldown<=0)shoot()
        spawnTimer-=dt
        if(spawnTimer<=0) { spawnScheduled();spawnTimer=max(0.24f,1.55f-time/145f) }
        if(time>=(bossCount+1)*90f && bossCount<5 && enemies.none { it.alive&&it.kind==EnemyKind.BOSS }) {
            if(spawnEnemy(EnemyKind.BOSS,1300f,500f,false)!=null) { bossCount++;bossBanner=3f;shake=0.3f }
        }
        enemies.forEach { if(it.alive)updateEnemy(it,dt) }
        projectiles.forEach { if(it.alive)updateProjectile(it,dt) }
        orbs.forEach { orb -> if(orb.alive) {
            val d2=distance2(x,y,orb.x,orb.y)
            if(d2<30f*30f) { xp+=orb.value;orb.alive=false }
            else if(d2<160f*160f) { val d=sqrt(d2);orb.x+=(x-orb.x)/d*320f*dt;orb.y+=(y-orb.y)/d*320f*dt }
        } }
        if(hp<=0) { phase=RunPhase.DYING;clearInput();shake=0.35f }
        else levelCheck()
    }
    private fun levelCheck() {
        if(xp<xpGoal)return
        xp-=xpGoal;level++;hp=min(maxHp,hp+8f)
        val candidates=Upgrade.entries.filter { rank(it)<it.cap }.toMutableList()
        val picked=ArrayList<Upgrade>(3)
        while(picked.size<3 && candidates.isNotEmpty()) {
            // Weighted rarity; choice stream never changes enemy spawn stream.
            val weights=candidates.map { when(it.rarity) { Rarity.COMMON->6;Rarity.RARE->4;Rarity.EPIC->2;Rarity.LEGENDARY->1 } }
            var roll=upgradeRandom.index(weights.sum());var i=0
            while(roll>=weights[i]) { roll-=weights[i];i++ }
            picked.add(candidates.removeAt(i))
        }
        if(picked.isNotEmpty()) { choices=picked;phase=RunPhase.UPGRADE;clearInput();shake=0.2f }
    }
    private fun movePlayer(dx: Float,dy: Float) {
        val nx=(x+dx).coerceIn(20f,RiftRules.WORLD-20f);if(!blocked(nx,y,17f))x=nx
        val ny=(y+dy).coerceIn(20f,RiftRules.WORLD-20f);if(!blocked(x,ny,17f))y=ny
    }
    private fun blocked(px: Float,py: Float,r: Float)=RiftRules.obstacles.any {
        val cx=px.coerceIn(it.x,it.x+it.w);val cy=py.coerceIn(it.y,it.y+it.h);distance2(px,py,cx,cy)<r*r
    }
    private fun spawnScheduled() {
        val roll=spawnRandom.unit()
        val kind=when { time>55 && roll<0.18->EnemyKind.TANK;time>32 && roll<0.4->EnemyKind.RANGED;time>18 && roll<0.6->EnemyKind.FAST;else->EnemyKind.MELEE }
        val angle=spawnRandom.unit()*PI.toFloat()*2f
        val sx=900f+cos(angle)*750f
        val sy=900f+sin(angle)*750f
        val elite=time>65 && spawnRandom.unit()<0.1f
        spawnEnemy(kind,sx,sy,elite)
    }
    internal fun spawnEnemy(kind: EnemyKind,sx: Float,sy: Float,elite: Boolean=false): Enemy? {
        val enemy=enemies.firstOrNull { !it.alive } ?: return null
        enemy.alive=true;enemy.serial=++serial;enemy.kind=kind;enemy.elite=elite
        enemy.radius=when(kind){EnemyKind.TANK->25f;EnemyKind.FAST->12f;EnemyKind.BOSS->46f;else->17f}
        enemy.speed=when(kind){EnemyKind.TANK->52f;EnemyKind.FAST->160f;EnemyKind.RANGED->75f;EnemyKind.BOSS->45f;else->90f}+min(time/20f,20f)
        val base=when(kind){EnemyKind.TANK->170f;EnemyKind.FAST->28f;EnemyKind.RANGED->45f;EnemyKind.BOSS->950f;else->48f}
        enemy.maxHp=base*(if(elite)2f else 1f)*(1f+min(time/600f,0.75f));enemy.hp=enemy.maxHp
        enemy.x=sx.coerceIn(enemy.radius,RiftRules.WORLD-enemy.radius);enemy.y=sy.coerceIn(enemy.radius,RiftRules.WORLD-enemy.radius)
        if(blocked(enemy.x,enemy.y,enemy.radius)) {
            var found=false
            for(i in 1..32) {
                val angle=(i%8)*PI.toFloat()/4f;val radius=((i-1)/8+1)*70f
                val px=(sx+cos(angle)*radius).coerceIn(enemy.radius,RiftRules.WORLD-enemy.radius)
                val py=(sy+sin(angle)*radius).coerceIn(enemy.radius,RiftRules.WORLD-enemy.radius)
                if(!blocked(px,py,enemy.radius)) { enemy.x=px;enemy.y=py;found=true;break }
            }
            if(!found) { enemy.alive=false;return null }
        }
        enemy.attack=1f;enemy.slow=0f;enemy.flash=0f;enemy.windup=0f;enemy.pattern=0;enemy.aimX=0f;enemy.aimY=0f
        return enemy
    }
    private fun shoot() {
        val target=enemies.filter { it.alive && distance2(x,y,it.x,it.y)<600f*600f }.minByOrNull { distance2(x,y,it.x,it.y) }
        var dx=target?.let { it.x-x } ?: facingX;var dy=target?.let { it.y-y } ?: facingY
        val length=max(0.001f,sqrt(dx*dx+dy*dy));dx/=length;dy/=length
        val dmg=character.webDamage*(1f+rank(Upgrade.DAMAGE)*0.25f)*(if(combatRandom.unit()<rank(Upgrade.CRITICAL)*0.12f)2f else 1f)
        projectile(x+dx*21,y+dy*21,dx*620f,dy*620f,true,dmg,rank(Upgrade.PIERCE),rank(Upgrade.RICOCHET)>0)
        attackCooldown=character.shotCooldown*0.82f.pow(rank(Upgrade.ATTACK_SPEED));attackFlash=0.15f
    }
    private fun projectile(px: Float,py: Float,vx: Float,vy: Float,friendly: Boolean,damage: Float,pierce: Int=0,branch: Boolean=false) {
        val p=projectiles.firstOrNull { !it.alive } ?: return
        p.alive=true;p.x=px;p.y=py;p.vx=vx;p.vy=vy;p.friendly=friendly;p.damage=damage;p.pierce=pierce;p.branch=branch;p.lastHit=-1;p.life=if(friendly)1.2f else 4f
    }
    private fun updateEnemy(e: Enemy,dt: Float) {
        e.attack-=dt;e.slow=max(0f,e.slow-dt);e.flash=max(0f,e.flash-dt)
        val dx=x-e.x;val dy=y-e.y;val d=max(0.001f,sqrt(dx*dx+dy*dy))
        var movement=1f
        if(e.kind==EnemyKind.RANGED) {
            movement=if(d<230f)-1f else if(d<360f)0f else 1f
            if(e.attack<=0 && d<650f) { projectile(e.x,e.y,dx/d*200f,dy/d*200f,false,10f);e.attack=2.1f }
        }
        if(e.kind==EnemyKind.BOSS) {
            if(e.windup>0) {
                e.windup-=dt;movement=0f
                if(e.windup<=0) {
                    if(e.pattern%2==0)for(i in 0..11) { val angle=i*PI.toFloat()/6f;projectile(e.x,e.y,cos(angle)*180f,sin(angle)*180f,false,16f) }
                    else { if(distance2(x,y,e.aimX,e.aimY)<150f*150f)hurtPlayer(25f);e.x=e.aimX;e.y=e.aimY;shake=0.2f }
                    e.attack=3.4f;e.pattern++
                }
            } else if(e.attack<=0) { e.windup=1.2f;e.aimX=x;e.aimY=y;movement=0f }
        }
        val speed=e.speed*(if(e.slow>0)0.35f else 1f)*movement*dt
        val nx=(e.x+dx/d*speed).coerceIn(e.radius,RiftRules.WORLD-e.radius)
        val ny=(e.y+dy/d*speed).coerceIn(e.radius,RiftRules.WORLD-e.radius)
        if(!blocked(nx,e.y,e.radius))e.x=nx
        if(!blocked(e.x,ny,e.radius))e.y=ny
        if(d<e.radius+17f)hurtPlayer(when(e.kind){EnemyKind.TANK->20f;EnemyKind.BOSS->24f;EnemyKind.FAST->7f;else->11f})
    }
    private fun updateProjectile(p: Projectile,dt: Float) {
        p.life-=dt;p.x+=p.vx*dt;p.y+=p.vy*dt
        if(p.life<=0 || p.x !in 0f..RiftRules.WORLD || p.y !in 0f..RiftRules.WORLD || blocked(p.x,p.y,4f)) { p.alive=false;return }
        if(!p.friendly) { if(distance2(x,y,p.x,p.y)<22f*22f) { hurtPlayer(p.damage);p.alive=false };return }
        for(e in enemies)if(e.alive && e.serial!=p.lastHit && distance2(e.x,e.y,p.x,p.y)<(e.radius+6f).pow(2)) {
            hit(e,p.damage);p.lastHit=e.serial;if(rank(Upgrade.SLOW)>0)e.slow=max(e.slow,1.5f)
            if(p.branch) {
                val target=enemies.filter { it.alive && it.serial!=e.serial && distance2(e.x,e.y,it.x,it.y)<210f*210f }.minByOrNull { distance2(e.x,e.y,it.x,it.y) }
                if(target!=null) { val dx=target.x-e.x;val dy=target.y-e.y;val d=max(0.001f,sqrt(dx*dx+dy*dy));projectile(e.x+dx/d*(e.radius+8f),e.y+dy/d*(e.radius+8f),dx/d*620f,dy/d*620f,true,p.damage*0.7f) }
                p.branch=false
            }
            if(p.pierce>0)p.pierce-- else {
                p.alive=false
                if(rank(Upgrade.EXPLOSION)>0) { enemies.forEach { if(it.alive && distance2(e.x,e.y,it.x,it.y)<90f*90f)hit(it,p.damage*0.6f) };shake=0.08f }
                break
            }
        }
    }
    private fun hurtPlayer(value: Float) {
        if(hurt>0||dashLeft>0||hp<=0)return
        hp=max(0f,hp-value*(1f-rank(Upgrade.ARMOR)*0.12f));hurt=0.65f;shake=0.15f;combo=0
    }
    private fun hit(e: Enemy,value: Float) {
        if(!e.alive)return
        damage+=min(e.hp,value);e.hp-=value;e.flash=0.12f
        if(e.hp>0)return
        e.alive=false;kills++;if(e.elite)elites++;if(e.kind==EnemyKind.BOSS)bosses++
        combo++;maxCombo=max(combo,maxCombo);comboTimer=4f
        val orb=orbs.firstOrNull { !it.alive }
        val xpValue=when { e.kind==EnemyKind.BOSS->60;e.elite->15;e.kind==EnemyKind.TANK->8;else->4 }
        if(orb!=null) { orb.alive=true;orb.x=e.x;orb.y=e.y;orb.value=xpValue }else xp+=xpValue
    }
    fun result()=RunResult(seed,character.key,time,kills,elites,bosses,damage,maxCombo,level,score,hp>0,
        Upgrade.entries.filter { rank(it)>0 }.associate { it.name to rank(it) })
    private fun distance2(ax: Float,ay: Float,bx: Float,by: Float)=(ax-bx)*(ax-bx)+(ay-by)*(ay-by)
}
