package com.example.marvellobby.rift.engine

import kotlin.math.*

object HeroKits {
    fun create(engine:RiftEngine):HeroCombatKit=when(engine.character.key) {
        "iron-man"->IronKit(engine)
        "hulk"->HulkKit(engine)
        "thor"->ThorKit(engine)
        "wolverine"->WolverineKit(engine)
        "doctor-strange"->StrangeKit(engine)
        else->SpiderCombatKit(engine)
    }
}

/** Shared charge and cooldown bookkeeping; each hero supplies combat decisions. */
abstract class ChargedHeroKit(protected val e:RiftEngine,initial:AttackStyle):HeroCombatKit {
    override var style=initial;protected set
    final override var ultimateCharge=if(e.tutorial)100f else 0f;private set
    final override var hyperCharge=if(e.tutorial)100f else 0f;private set
    final override var hyperLeft=0f;private set
    override var gadgetCooldown=0f;protected set
    override fun damage(amount:Float) {
        ultimateCharge=min(100f,ultimateCharge+amount*100/e.character.ultCost)
        if(hyperLeft<=0)hyperCharge=min(100f,hyperCharge+amount*100/e.character.hyperCost)
    }
    override fun tick(dt:Float) {hyperLeft=max(0f,hyperLeft-dt);gadgetCooldown=max(0f,gadgetCooldown-dt);heroTick(dt)}
    protected open fun heroTick(dt:Float) {}
    final override fun gadget():Boolean {if(gadgetCooldown>0)return false;return useGadget()}
    protected abstract fun useGadget():Boolean
    final override fun ultimate(px:Float,py:Float):Boolean {
        if(ultimateCharge<100&&!e.tutorial)return false
        if(!useUltimate(px,py))return false
        ultimateCharge=0f;if(e.tutorial)e.advanceTutorialUltimate();return true
    }
    protected abstract fun useUltimate(px:Float,py:Float):Boolean
    override fun hyper():Boolean {if(hyperLeft>0||hyperCharge<100&&!e.tutorial)return false;hyperCharge=0f;hyperLeft=15f;return true}
    protected fun direction():Pair<Float,Float> = if(e.aiming)e.aimX to e.aimY else e.facingX to e.facingY
}

class IronKit(e:RiftEngine):ChargedHeroKit(e,AttackStyle.RANGED) {
    override val ultimateRadius get()=145f+e.rank(Upgrade.SPECIAL)*20
    private var precision=0f
    override val speedMultiplier get()=if(hyperLeft>0)1.2f else 1f
    override fun heroTick(dt:Float){precision=max(0f,precision-dt)}
    override fun attack(dx:Float,dy:Float,held:Float)=e.fireDirected(dx,dy,false,if(precision>0)1.3f else 1f,if(precision>0||hyperLeft>0)1 else 0)
    override fun useGadget():Boolean {precision=5f;gadgetCooldown=8f;e.dash(true);return true}
    override fun useUltimate(px:Float,py:Float):Boolean {
        // A directed impact rewards distance without moving the shooter.
        e.areaStrike(px,py,145f+e.rank(Upgrade.SPECIAL)*20,145f,0f);return true
    }
}

class HulkKit(e:RiftEngine):ChargedHeroKit(e,AttackStyle.MELEE) {
    override val gadgetDuration get()=6f
    override val ultimateRadius get()=235f+e.rank(Upgrade.SPECIAL)*20
    override val damageReceived get()=if(hyperLeft>0).65f else .9f
    override fun attack(dx:Float,dy:Float,held:Float):Boolean {
        val hit=e.directedMelee(dx,dy,if(hyperLeft>0)1.25f else 1f,if(hyperLeft>0)1.15f else 1f)
        if(hit&&hyperLeft>0)e.areaStrike(e.x+dx*90,e.y+dy*90,105f,18f,1f)
        return hit
    }
    override fun useGadget():Boolean {
        val (dx,dy)=direction();e.chargedStrike(dx,dy,110f,35f);gadgetCooldown=6f;return true
    }
    override fun useUltimate(px:Float,py:Float):Boolean {
        if(!e.reposition(px,py))return false
        e.areaStrike(e.x,e.y,235f+e.rank(Upgrade.SPECIAL)*20,115f,1f);return true
    }
}

class ThorKit(e:RiftEngine):ChargedHeroKit(e,AttackStyle.MELEE) {
    override val ultimateRadius get()=205f+e.rank(Upgrade.SPECIAL)*20
    override fun useGadget():Boolean {style=if(style==AttackStyle.MELEE)AttackStyle.RANGED else AttackStyle.MELEE;e.cancelAttack();return true}
    override fun attack(dx:Float,dy:Float,held:Float):Boolean {
        val result=if(style==AttackStyle.RANGED)e.fireDirected(dx,dy,false,1f,if(hyperLeft>0)2 else 0)
            else e.directedMelee(dx,dy,1f,1f)
        if(result&&hyperLeft>0)e.areaStrike(e.x+dx*110,e.y+dy*110,125f,16f,1.2f)
        return result
    }
    override fun useUltimate(px:Float,py:Float):Boolean {e.areaStrike(px,py,205f+e.rank(Upgrade.SPECIAL)*20,110f,3f);return true}
}

class WolverineKit(e:RiftEngine):ChargedHeroKit(e,AttackStyle.MELEE) {
    override val gadgetDuration get()=4.5f
    override val ultimateIsRush get()=true
    override val ultimateRange get()=320f+e.rank(Upgrade.SPECIAL)*30
    override val ultimateRadius get()=45f
    override val speedMultiplier get()=if(hyperLeft>0)1.24f else 1f
    override fun heroTick(dt:Float){if(e.hurt<=0)e.heal(dt*if(hyperLeft>0)5f else 1.5f)}
    override fun attack(dx:Float,dy:Float,held:Float):Boolean {
        val damageBefore=e.damage
        val result=e.directedMelee(dx,dy,1f,if(hyperLeft>0)1.25f else 1f)
        if(result&&e.damage>damageBefore&&hyperLeft>0)e.heal(1.5f)
        return result
    }
    override fun useGadget():Boolean {
        val (dx,dy)=direction();e.chargedStrike(dx,dy,155f,20f);gadgetCooldown=4.5f;return true
    }
    override fun useUltimate(px:Float,py:Float):Boolean {
        val dx=px-e.x;val dy=py-e.y;val n=max(1f,sqrt(dx*dx+dy*dy))
        e.chargedStrike(dx/n,dy/n,min(n,320f+e.rank(Upgrade.SPECIAL)*30),95f);return true
    }
}

class StrangeKit(e:RiftEngine):ChargedHeroKit(e,AttackStyle.RANGED) {
    override val gadgetDuration get()=7f
    override val ultimateRadius get()=180f+e.rank(Upgrade.SPECIAL)*20
    private var zoneLeft=0f;private var pulse=0f;private var zoneX=0f;private var zoneY=0f
    override fun attack(dx:Float,dy:Float,held:Float)=e.fireDirected(dx,dy,hyperLeft>0,.95f,if(hyperLeft>0)1 else 0)
    override fun useGadget():Boolean {
        val (dx,dy)=direction()
        if(!e.reposition(e.x+dx*190,e.y+dy*190))return false
        e.areaStrike(e.x,e.y,95f,0f,2f);gadgetCooldown=7f;return true
    }
    override fun useUltimate(px:Float,py:Float):Boolean {
        zoneX=px;zoneY=py;zoneLeft=3.1f;pulse=0f;e.areaStrike(px,py,180f+e.rank(Upgrade.SPECIAL)*20,40f,2f);return true
    }
    override fun heroTick(dt:Float) {
        if(zoneLeft<=0)return
        zoneLeft-=dt;pulse+=dt
        if(pulse>=1f){pulse-=1f;e.areaStrike(zoneX,zoneY,180f+e.rank(Upgrade.SPECIAL)*20,25f,2f)}
    }
}
