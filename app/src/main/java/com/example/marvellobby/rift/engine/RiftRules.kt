package com.example.marvellobby.rift.engine

import kotlin.math.*

enum class RunPhase { PLAYING, PAUSED, UPGRADE, DYING, FINISHED }
enum class AnimationState { IDLE, RUN, ATTACK, DASH, HIT, DEATH, SPECIAL }
enum class EnemyKind { MELEE, RANGED, TANK, FAST, BOSS }
enum class Rarity { COMMON, RARE, EPIC, LEGENDARY }
enum class Upgrade(val rarity: Rarity,val cap: Int,val title: String,val description: String) {
    DAMAGE(Rarity.COMMON,4,"Web Shot","+25% web and melee damage / +25% dano de teia e golpes"),
    SPEED(Rarity.COMMON,3,"Spider Sense","+12% movement / +12% movimento"),
    ATTACK_SPEED(Rarity.RARE,3,"Quick Hands","Faster web recharge / Recarga de teia mais rápida"),
    HEALTH(Rarity.COMMON,3,"Second Wind","+25 health and heal / +25 vida e cura"),
    ARMOR(Rarity.RARE,3,"Resilient","Reduce incoming damage / Reduz o dano recebido"),
    DASH(Rarity.RARE,3,"Air Step","Shorter dash cooldown / Menor intervalo da esquiva"),
    CRITICAL(Rarity.RARE,3,"Perfect Timing","+12% critical chance / +12% chance crítica"),
    PIERCE(Rarity.EPIC,2,"Thread the Needle","Webs pierce another target / Teias atravessam outro alvo"),
    RICOCHET(Rarity.EPIC,1,"Ricochet Web","One web branches to a nearby enemy / Uma teia salta para outro inimigo"),
    EXPLOSION(Rarity.LEGENDARY,1,"Web Explosion","Final hit blasts nearby enemies / Último impacto explode em área"),
    SLOW(Rarity.RARE,1,"Sticky Web","Longer web slow / Teias prendem por mais tempo"),
    SPECIAL(Rarity.EPIC,3,"Web Storm","Wider burst, shorter cooldown / Explosão maior, menor intervalo")
}
fun Upgrade.displayName(language: String,character:String="spider-man"):String {
    if(character!="spider-man")return if(language=="pt")when(this){Upgrade.DAMAGE->"Dano ampliado";Upgrade.ATTACK_SPEED->"Ritmo de ataque";Upgrade.PIERCE->"Alcance / perfuração";Upgrade.RICOCHET->"Impacto secundário";Upgrade.EXPLOSION->"Impacto em área";Upgrade.SLOW->"Controle prolongado";Upgrade.SPECIAL->"Ultimate ampliada";else->displayName(language)}else when(this){Upgrade.DAMAGE->"Damage boost";Upgrade.ATTACK_SPEED->"Attack tempo";Upgrade.PIERCE->"Reach / piercing";Upgrade.RICOCHET->"Secondary impact";Upgrade.EXPLOSION->"Area impact";Upgrade.SLOW->"Extended control";Upgrade.SPECIAL->"Ultimate boost";else->title}
    return if(language!="pt")title else when(this) {
    Upgrade.DAMAGE->"Teia potente";Upgrade.SPEED->"Sentido aranha";Upgrade.ATTACK_SPEED->"Mãos rápidas"
    Upgrade.HEALTH->"Novo fôlego";Upgrade.ARMOR->"Resistência";Upgrade.DASH->"Passo aéreo"
    Upgrade.CRITICAL->"Momento perfeito";Upgrade.PIERCE->"Teia perfurante";Upgrade.RICOCHET->"Teia ricochete"
    Upgrade.EXPLOSION->"Explosão de teia";Upgrade.SLOW->"Teia pegajosa";Upgrade.SPECIAL->"Tempestade de teia"
}
}
fun Upgrade.descriptionFor(language:String,character:String):String {
    if(character=="spider-man")return description.split(" / ").let{if(language=="pt")it.last()else it.first()}
    val text=when(this){Upgrade.DAMAGE->"+25% primary damage / +25% dano principal";Upgrade.ATTACK_SPEED->"Faster attacks and resource recharge / Ataques e recarga mais rápidos";Upgrade.PIERCE->"More projectile piercing or melee reach / Mais perfuração ou alcance melee";Upgrade.RICOCHET->"Projectile branching or melee combo impact / Salto de projétil ou impacto no combo melee";Upgrade.EXPLOSION->"Projectile or melee combo blasts / Explosão de projétil ou combo melee";Upgrade.SLOW->"Longer projectile control or melee slow / Controle de projétil ampliado ou slow no melee";Upgrade.SPECIAL->"Empowers the Ultimate / Amplia a Ultimate";else->description}
    return text.split(" / ").let{if(language=="pt")it.last()else it.first()}
}

data class CharacterDefinition(val key: String,val catalogId: Int,val name: String,val speed: Float,val health: Float,
    val webDamage: Float,val shotCooldown: Float,val dashCooldown: Float,val specialCooldown: Float,
    val hasRanged: Boolean=true,val meleeDamage: Float=34f,val meleeReach: Float=85f,val webRecharge: Float=1.8f,
    val projectileSpeed:Float=620f,val attackRange:Float=744f,val slowDuration:Float=2f,val color:Int=0xFFF02A3D.toInt(),
    val ultCost:Float=400f,val hyperCost:Float=1000f,val meleeInterval:Float=.28f)
object RiftCharacters {
    val spider=CharacterDefinition("spider-man",1443,"Spider-Man",210f,100f,23f,0.3f,2.8f,12f)
    val iron=CharacterDefinition("iron-man",0,"Iron Man",215f,95f,28f,.28f,2.5f,12f,webRecharge=.7f,projectileSpeed=880f,attackRange=1000f,slowDuration=0f,color=0xFFFFBD54.toInt(),ultCost=480f,hyperCost=1150f)
    val hulk=CharacterDefinition("hulk",0,"Hulk",180f,185f,0f,.55f,3.5f,12f,false,58f,78f,2.2f,color=0xFF80D267.toInt(),ultCost=580f,hyperCost=1450f,meleeInterval=.5f)
    val thor=CharacterDefinition("thor",0,"Thor",192f,140f,32f,.5f,3.2f,12f,true,43f,98f,1.1f,540f,680f,1f,0xFF89D6FF.toInt(),520f,1300f,.38f)
    val wolverine=CharacterDefinition("wolverine",0,"Wolverine",232f,110f,0f,.2f,2.2f,12f,false,24f,68f,1.5f,color=0xFFFFDA53.toInt(),ultCost=420f,hyperCost=1100f,meleeInterval=.18f)
    val strange=CharacterDefinition("doctor-strange",0,"Doctor Strange",198f,90f,26f,.42f,3f,12f,webRecharge=.95f,projectileSpeed=500f,attackRange=760f,slowDuration=1.5f,color=0xFFFF9D52.toInt(),ultCost=450f,hyperCost=1200f)
    val all=listOf(spider,iron,hulk,thor,wolverine,strange)
    fun get(key: String)=all.firstOrNull { it.key==key } ?: spider
}
data class Obstacle(val x: Float,val y: Float,val w: Float,val h: Float)
object RiftRules {
    const val VERSION="rift-3"
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
    val volleys=IntArray(8);var volleyCursor=0
    var alive=false;var serial=0;var kind=EnemyKind.MELEE;var elite=false
    var x=0f;var y=0f;var hp=0f;var maxHp=0f;var radius=16f;var speed=85f;var attack=0f
    var slow=0f;var flash=0f;var windup=0f;var pattern=0;var aimX=0f;var aimY=0f
}
class Projectile {
    var volley=0
    var alive=false;var friendly=true;var x=0f;var y=0f;var vx=0f;var vy=0f;var life=0f;var damage=0f
    var pierce=0;var branch=false;var lastHit=-1
}
class XpOrb { var alive=false;var x=0f;var y=0f;var value=0 }
class HitEffect {var life=0f;var x=0f;var y=0f;var damage=0;var web=false}
data class RunResult(val seed: Int,val character: String,val duration: Float,val kills: Int,val elites: Int,
    val bosses: Int,val damage: Float,val maxCombo: Int,val level: Int,val score: Int,val extracted: Boolean,
    val upgrades: Map<String,Int>,val version: String=RiftRules.VERSION)

/** Mutable pools are owned exclusively by the game thread (Android's UI thread in this slice). */
class RiftEngine(val seed: Int,val character: CharacterDefinition=RiftCharacters.spider,val tutorial: Boolean=false) {
    val kit: HeroCombatKit=HeroKits.create(this)
    var attackHeld=0f;private set
    var ultimateAiming=false;private set;var ultimateX=900f;private set;var ultimateY=900f;private set
    private var volleyId=0
    private var chargingAbilities=true
    var lungeLeft=0f;private set;var lungeFromX=0f;private set;var lungeFromY=0f;private set
    private val chargedHits=IntArray(RiftRules.MAX_ENEMIES)
    private var attackPressed=false
    fun beginAttack(){attackHeld=0f;attackPressed=true}
    fun cancelAttack(){aiming=false;attackHeld=0f;attackPressed=false}
    var modeFlash=0f;private set
    fun gadget():Boolean {if(!canAct()||!kit.gadget())return false;modeFlash=.6f;return true}
    fun hyper():Boolean=canAct()&&kit.hyper()
    private fun canAct()=phase==RunPhase.PLAYING&&transition<=0&&levelUpLeft<=0
    fun aimUltimate(dx:Float,dy:Float){val n=max(1f,sqrt(dx*dx+dy*dy));ultimateAiming=true;ultimateX=(x+dx/n*kit.ultimateRange).coerceIn(0f,RiftRules.WORLD);ultimateY=(y+dy/n*kit.ultimateRange).coerceIn(0f,RiftRules.WORLD)}
    fun releaseUltimate():Boolean {val aimed=ultimateAiming;ultimateAiming=false;return aimed&&canAct()&&kit.ultimate(ultimateX,ultimateY)}
    fun releaseAttack(auto:Boolean=false):Boolean {
        if(!canAct()){cancelAttack();return false}
        if(auto) {
            val limit=if(kit.style==AttackStyle.MELEE)character.meleeReach+60 else character.attackRange
            val target=enemies.filter{it.alive&&distance2(x,y,it.x,it.y)<limit*limit}.minByOrNull{distance2(x,y,it.x,it.y)}
            val dx=target?.let{it.x-x}?:facingX;val dy=target?.let{it.y-y}?:facingY;aim(dx,dy)
        }
        val valid=aiming;val held=attackHeld;val dx=aimX;val dy=aimY
        cancelAttack();return valid&&kit.attack(dx,dy,held)
    }
    internal fun consumeCharge():Boolean {if(webCharges<=0)return false;webCharges--;return true}
    internal fun fireDirected(dx:Float,dy:Float,spread:Boolean,power:Float=1f,pierce:Int=0):Boolean {
        if(!character.hasRanged||attackCooldown>0||projectiles.count{!it.alive}<(if(spread)3 else 1)||!consumeCharge())return false
        val group=++volleyId
        shoot(dx,dy,group,power,pierce)
        if(spread)for(angle in floatArrayOf(-.14f,.14f))shoot(dx*cos(angle)-dy*sin(angle),dx*sin(angle)+dy*cos(angle),group,power,pierce)
        if(tutorial&&tutorialStep==1)tutorialStep=2
        return true
    }
    internal fun directedMelee(dx:Float,dy:Float,reach:Float=1f,power:Float=1f):Boolean {
        aim(dx,dy);facingX=aimX;facingY=aimY;val result=melee(reach,power);aiming=false;return result
    }
    internal fun chargedStrike(dx:Float,dy:Float,distance:Float,power:Float) {
        lungeFromX=x;lungeFromY=y;lungeLeft=.25f;facingX=dx;facingY=dy;chargedHits.fill(0);meleeFlash=.35f;shake=.15f;attackFlash=.35f
        for(step in 1..12){movePlayer(dx*distance/12,dy*distance/12)
            enemies.forEachIndexed {i,e->if(e.alive&&chargedHits[i]!=e.serial&&distance2(x,y,e.x,e.y)<(e.radius+45f).pow(2)){chargedHits[i]=e.serial;hit(e,power);val nx=e.x+dx*32;val ny=e.y+dy*32;if(!blocked(nx,ny,e.radius)){e.x=nx.coerceIn(e.radius,RiftRules.WORLD-e.radius);e.y=ny.coerceIn(e.radius,RiftRules.WORLD-e.radius)}}}
        };meleeCooldown=.5f
    }
    internal fun areaStrike(px:Float,py:Float,radius:Float,power:Float,slow:Float) {
        chargingAbilities=false
        enemies.forEach{if(it.alive&&distance2(px,py,it.x,it.y)<radius*radius){hit(it,power);it.slow=max(it.slow,slow)}}
        chargingAbilities=true;explosionX=px;explosionY=py;explosionRadius=radius;explosionLifetime=.5f;explosionFlash=.5f;shake=.2f
    }
    internal fun advanceTutorialUltimate(){if(tutorialStep==3)tutorialStep=4}
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
    var explosionRadius=90f;private set;var explosionLifetime=.35f;private set;var explosionFlash=0f;private set;var explosionX=0f;private set;var explosionY=0f;private set
    var choices: List<Upgrade> = emptyList();private set
    val upgrades=IntArray(Upgrade.entries.size)
    val enemies=Array(RiftRules.MAX_ENEMIES) { Enemy() }
    val projectiles=Array(RiftRules.MAX_PROJECTILES) { Projectile() }
    val orbs=Array(128) { XpOrb() }
    val effects=Array(48){HitEffect()}
    var levelUpLeft=0f;private set
    private var choosingLevel=false
    private val spawnRandom=RiftRandom(seed);private val combatRandom=RiftRandom(seed xor 0x546A17)
    private val upgradeRandom=RiftRandom(seed xor 0x733CA9)
    private var spawnTimer=1f;private var serial=0;private var bossCount=0;private var attackCooldown=0f;private var dying=0f
    var moveX=0f;var moveY=0f;var attacking=false
    var aimX=0f;private set;var aimY=-1f;private set;var aiming=false;private set
    var webCharges=3;private set;var recharge=0f;private set
    var meleeCooldown=0f;private set;var meleeFlash=0f;private set;var meleeCombo=0;private set
    private var meleeChain=0f
    var wave=1;private set;var transition=if(tutorial)0f else 1.5f;private set
    private var nextWaveAt=30f
    var transitionKind="start";private set;private var pendingBoss=false
    var tutorialStep=0;private set;private var tutorialMoved=0f
    internal fun heal(amount:Float){hp=min(maxHp,hp+amount)}
    internal fun reposition(px:Float,py:Float):Boolean {val nx=px.coerceIn(18f,RiftRules.WORLD-18f);val ny=py.coerceIn(18f,RiftRules.WORLD-18f);if(blocked(nx,ny,18f))return false;x=nx;y=ny;return true}
    var feedback="";private set;var feedbackLeft=0f;private set
    val rechargeDuration get()=character.webRecharge*0.82f.pow(rank(Upgrade.ATTACK_SPEED))
    fun aim(dx: Float,dy: Float) { val length=sqrt(dx*dx+dy*dy);aiming=length>0.12f;if(aiming){aimX=dx/length;aimY=dy/length} }
    fun releaseWeb(): Boolean {
        val valid=aiming;aiming=false
        if(!valid||!character.hasRanged||phase!=RunPhase.PLAYING||levelUpLeft>0||transition>0||webCharges<=0||attackCooldown>0||projectiles.none{!it.alive})return false
        shoot(aimX,aimY);webCharges--;if(tutorial&&tutorialStep==1)tutorialStep=2;return true
    }
    fun melee(reach:Float=1f,power:Float=1f): Boolean {
        if(phase!=RunPhase.PLAYING||levelUpLeft>0||transition>0||meleeCooldown>0)return false
        meleeCombo=if(meleeChain>0)meleeCombo%3+1 else 1;meleeChain=1.1f
        meleeCooldown=(character.meleeInterval*0.85f.pow(rank(Upgrade.ATTACK_SPEED))*(if(meleeCombo==3)1.7f else 1f))/(if(kit.hyperLeft>0)1.15f else 1f);meleeFlash=0.22f;attackFlash=0.22f
        val fx=if(aiming)aimX else facingX;val fy=if(aiming)aimY else facingY
        enemies.forEach { e->if(e.alive) {
            val dx=e.x-x;val dy=e.y-y;val length=max(1f,sqrt(dx*dx+dy*dy))
            if(length<character.meleeReach*reach*(1f+rank(Upgrade.PIERCE)*.1f)+e.radius && lineOpen(x,y,e.x,e.y) && (dx*fx+dy*fy)/length> -0.15f) {
                hit(e,character.meleeDamage*power*(if(meleeCombo==3)1.65f else 1f)*(1f+rank(Upgrade.DAMAGE)*0.25f)*(if(combatRandom.unit()<rank(Upgrade.CRITICAL)*.12f)2f else 1f))
                val nx=e.x+dx/length*22f;val ny=e.y+dy/length*22f
                if(!blocked(nx,ny,e.radius)){e.x=nx.coerceIn(e.radius,RiftRules.WORLD-e.radius);e.y=ny.coerceIn(e.radius,RiftRules.WORLD-e.radius)}
                if(rank(Upgrade.SLOW)>0)e.slow=max(e.slow,1.5f)
                shake=0.08f
            }
        } }
        if(!character.hasRanged&&meleeCombo==3&&(rank(Upgrade.EXPLOSION)>0||rank(Upgrade.RICOCHET)>0))areaStrike(x+fx*70,y+fy*70,100f,character.meleeDamage*.35f*(rank(Upgrade.EXPLOSION)+rank(Upgrade.RICOCHET)),1f)
        if(tutorial&&tutorialStep==2)tutorialStep=3
        return true
    }
    fun tutorialNext() { if(tutorial&&tutorialStep in 4..5)tutorialStep++ }
    val aimRange: Float get() {
        var distance=24f
        while(distance<character.attackRange){val px=x+aimX*distance;val py=y+aimY*distance
            if(px !in 0f..RiftRules.WORLD||py !in 0f..RiftRules.WORLD||blocked(px,py,4f))return distance
            distance+=8f
        };return character.attackRange
    }
    val xpGoal get()=12+(level-1)*8
    val deathProgress get()=(dying/.8f).coerceIn(0f,1f)
    val score get()=RiftRules.score(time,kills,elites,bosses,damage,maxCombo)
    val dashDuration get()=character.dashCooldown*0.8f.pow(rank(Upgrade.DASH))
    val specialDuration get()=character.specialCooldown*0.85f.pow(rank(Upgrade.SPECIAL))
    val animation get()=when { phase==RunPhase.DYING||phase==RunPhase.FINISHED->AnimationState.DEATH;dashLeft>0->AnimationState.DASH;
        hurt>0.45f->AnimationState.HIT;specialFlash>0->AnimationState.SPECIAL;attackFlash>0->AnimationState.ATTACK;
        moveX*moveX+moveY*moveY>0.01f->AnimationState.RUN;else->AnimationState.IDLE }
    fun rank(u: Upgrade)=upgrades[u.ordinal]
    fun pause() { if(phase==RunPhase.PLAYING)phase=RunPhase.PAUSED;clearInput() }
    fun resume() { if(phase==RunPhase.PAUSED)phase=RunPhase.PLAYING }
    fun clearInput() { moveX=0f;moveY=0f;attacking=false;cancelAttack();ultimateAiming=false }
    fun dash(ignoreCooldown:Boolean=false): Boolean {
        if(phase!=RunPhase.PLAYING||transition>0||(!ignoreCooldown&&dashCooldown>0))return false
        val length=sqrt(moveX*moveX+moveY*moveY)
        if(length>0.1f) { facingX=moveX/length;facingY=moveY/length }
        dashLeft=0.18f;dashCooldown=dashDuration;return true
    }
    fun special(): Boolean {
        if(phase!=RunPhase.PLAYING||transition>0||specialCooldown>0)return false
        specialCooldown=specialDuration;specialFlash=0.5f;shake=0.25f
        val radius=220f+rank(Upgrade.SPECIAL)*35f
        enemies.forEach { if(it.alive && distance2(x,y,it.x,it.y)<radius*radius) { hit(it,70f+rank(Upgrade.SPECIAL)*20f);it.slow=max(it.slow,3f) } }
        if(tutorial&&tutorialStep==3)tutorialStep=4
        return true
    }
    fun choose(upgrade: Upgrade): Boolean {
        if(phase!=RunPhase.UPGRADE||upgrade !in choices||rank(upgrade)>=upgrade.cap)return false
        upgrades[upgrade.ordinal]++
        if(upgrade==Upgrade.HEALTH) { maxHp+=25f;hp=min(maxHp,hp+40f) }
        feedback=upgrade.name;feedbackLeft=1.4f;transition=0.8f;transitionKind="upgrade"
        choices=emptyList();phase=RunPhase.PLAYING;levelCheck();return true
    }
    fun step() {
        val dt=RiftRules.STEP
        effects.forEach {if(it.life>0)it.life=max(0f,it.life-dt)}
        if(phase==RunPhase.DYING) { dying+=dt;if(dying>=0.8f)phase=RunPhase.FINISHED;return }
        if(phase!=RunPhase.PLAYING)return
        if(choosingLevel) {
            // A short slowdown bridges combat and the paused choice screen; gameplay time stays frozen.
            levelUpLeft=max(0f,levelUpLeft-dt)
            enemies.forEach {if(it.alive)it.flash=max(0f,it.flash-dt)}
            projectiles.forEach {if(it.alive)updateProjectile(it,dt*.12f)}
            clearInput()
            if(levelUpLeft==0f){choosingLevel=false;phase=RunPhase.UPGRADE}
            return
        }
        if(transition>0) {
            transition=max(0f,transition-dt);clearInput()
            if(transition==0f&&pendingBoss) { spawnEnemy(EnemyKind.BOSS,1300f,500f);bossCount++;pendingBoss=false;bossBanner=0f }
            return
        }
        tick++;time=tick*dt
        lungeLeft=max(0f,lungeLeft-dt);modeFlash=max(0f,modeFlash-dt);kit.tick(dt);if(attackPressed)attackHeld+=dt
        if(time>=RiftRules.LIMIT_SECONDS) { phase=RunPhase.FINISHED;clearInput();return }
        dashCooldown=max(0f,dashCooldown-dt);specialCooldown=max(0f,specialCooldown-dt)
        attackCooldown=max(0f,attackCooldown-dt);hurt=max(0f,hurt-dt);shake=max(0f,shake-dt)
        attackFlash=max(0f,attackFlash-dt);specialFlash=max(0f,specialFlash-dt);bossBanner=max(0f,bossBanner-dt)
        explosionFlash=max(0f,explosionFlash-dt)
        meleeCooldown=max(0f,meleeCooldown-dt);meleeFlash=max(0f,meleeFlash-dt);meleeChain=max(0f,meleeChain-dt);feedbackLeft=max(0f,feedbackLeft-dt)
        if(webCharges<3) { recharge+=dt;if(recharge>=rechargeDuration){webCharges++;recharge=0f} }
        comboTimer-=dt;if(comboTimer<=0)combo=0
        val length=sqrt(moveX*moveX+moveY*moveY)
        val mx=moveX/max(1f,length);val my=moveY/max(1f,length)
        if(length>0.1f && dashLeft<=0) { facingX=mx/max(0.001f,sqrt(mx*mx+my*my));facingY=my/max(0.001f,sqrt(mx*mx+my*my)) }
        val speed=character.speed*kit.speedMultiplier*(1f+rank(Upgrade.SPEED)*0.12f)
        if(dashLeft>0) { movePlayer(facingX*speed*3.8f*dt,facingY*speed*3.8f*dt);dashLeft=max(0f,dashLeft-dt) }
        else movePlayer(mx*speed*dt,my*speed*dt)
        if(tutorial) {
            if(tutorialStep==0) { tutorialMoved+=sqrt(mx*mx+my*my)*speed*dt;if(tutorialMoved>65f)tutorialStep=1 }
            projectiles.forEach { if(it.alive)updateProjectile(it,dt) };return
        }
        if(time>=nextWaveAt && enemies.none { it.alive&&it.kind==EnemyKind.BOSS }) {
            wave++;nextWaveAt=time+30f;transition=2.4f;transitionKind="wave";enemies.forEach { it.alive=false };projectiles.forEach { it.alive=false };clearInput();return
        }
        spawnTimer-=dt
        if(spawnTimer<=0) { spawnScheduled();spawnTimer=max(0.24f,1.55f-time/145f) }
        if(time>=(bossCount+1)*90f && bossCount<5 && enemies.none { it.alive&&it.kind==EnemyKind.BOSS }) {
            pendingBoss=true;transition=2.4f;transitionKind="boss";bossBanner=2.4f
            enemies.forEach { it.alive=false };projectiles.forEach { it.alive=false };clearInput();return
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
        if(picked.isNotEmpty()) { choices=picked;choosingLevel=true;levelUpLeft=.65f;clearInput();shake=0.2f }
    }
    private fun movePlayer(dx: Float,dy: Float) {
        val nx=(x+dx).coerceIn(20f,RiftRules.WORLD-20f);if(!blocked(nx,y,17f))x=nx
        val ny=(y+dy).coerceIn(20f,RiftRules.WORLD-20f);if(!blocked(x,ny,17f))y=ny
    }
    private fun lineOpen(ax:Float,ay:Float,bx:Float,by:Float):Boolean {
        val dx=bx-ax;val dy=by-ay;val count=max(1,ceil(sqrt(dx*dx+dy*dy)/8f).toInt())
        for(i in 1 until count)if(blocked(ax+dx*i/count,ay+dy*i/count,1f))return false
        return true
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
        enemy.volleys.fill(0);enemy.volleyCursor=0;enemy.alive=true;enemy.serial=++serial;enemy.kind=kind;enemy.elite=elite
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
    private fun shoot(dx: Float,dy: Float,group:Int=0,power:Float=1f,pierce:Int=0) {
        val dmg=character.webDamage*power*(1f+rank(Upgrade.DAMAGE)*0.25f)*(if(combatRandom.unit()<rank(Upgrade.CRITICAL)*0.12f)2f else 1f)
        projectile(x+dx*21,y+dy*21,dx*character.projectileSpeed,dy*character.projectileSpeed,true,dmg,rank(Upgrade.PIERCE)+pierce,rank(Upgrade.RICOCHET)>0)?.volley=group
        attackCooldown=character.shotCooldown*0.82f.pow(rank(Upgrade.ATTACK_SPEED));attackFlash=0.15f
    }
    private fun projectile(px: Float,py: Float,vx: Float,vy: Float,friendly: Boolean,damage: Float,pierce: Int=0,branch: Boolean=false): Projectile? {
        val p=projectiles.firstOrNull { !it.alive } ?: return null
        p.alive=true;p.x=px;p.y=py;p.vx=vx;p.vy=vy;p.friendly=friendly;p.damage=damage;p.pierce=pierce;p.branch=branch;p.lastHit=-1;p.life=if(friendly)character.attackRange/character.projectileSpeed else 4f
        p.volley=0;return p
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
        val speed=e.speed*(if(e.slow>0)0.5f else 1f)*movement*dt
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
            if(p.volley>0&&p.volley in e.volleys){p.alive=false;break}
            if(p.volley>0){e.volleys[e.volleyCursor]=p.volley;e.volleyCursor=(e.volleyCursor+1)%e.volleys.size}
            hit(e,p.damage);p.lastHit=e.serial;e.slow=max(e.slow,character.slowDuration+rank(Upgrade.SLOW)*1.5f)
            if(p.branch) {
                val target=enemies.filter { it.alive && it.serial!=e.serial && distance2(e.x,e.y,it.x,it.y)<210f*210f }.minByOrNull { distance2(e.x,e.y,it.x,it.y) }
                if(target!=null) { val dx=target.x-e.x;val dy=target.y-e.y;val d=max(0.001f,sqrt(dx*dx+dy*dy));projectile(e.x+dx/d*(e.radius+8f),e.y+dy/d*(e.radius+8f),dx/d*620f,dy/d*620f,true,p.damage*0.7f)?.volley=p.volley }
                p.branch=false
            }
            if(p.pierce>0)p.pierce-- else {
                p.alive=false
                if(rank(Upgrade.EXPLOSION)>0) { enemies.forEach { if(it.alive && distance2(e.x,e.y,it.x,it.y)<90f*90f && (p.volley==0||p.volley !in it.volleys)){if(p.volley>0){it.volleys[it.volleyCursor]=p.volley;it.volleyCursor=(it.volleyCursor+1)%it.volleys.size};hit(it,p.damage*0.6f);it.slow=max(it.slow,2f+rank(Upgrade.SLOW)*1.5f)} };explosionX=e.x;explosionY=e.y;explosionRadius=90f;explosionLifetime=.35f;explosionFlash=.35f;shake=0.08f }
                break
            }
        }
    }
    private fun hurtPlayer(value: Float) {
        if(hurt>0||dashLeft>0||hp<=0)return
        hp=max(0f,hp-value*kit.damageReceived*(1f-rank(Upgrade.ARMOR)*0.12f));hurt=0.65f;shake=0.15f;combo=0
    }
    private fun hit(e: Enemy,value: Float) {
        if(!e.alive)return
        effects.firstOrNull {it.life<=0}?.let {it.life=.55f;it.x=e.x;it.y=e.y;it.damage=value.toInt();it.web=meleeFlash<=0}
        damage+=min(e.hp,value);e.hp-=value;e.flash=0.12f
        if(chargingAbilities)kit.damage(min(value,e.hp+value).coerceAtLeast(0f))
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
