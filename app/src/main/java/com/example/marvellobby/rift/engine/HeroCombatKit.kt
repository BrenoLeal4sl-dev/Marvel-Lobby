package com.example.marvellobby.rift.engine

import kotlin.math.*

enum class AttackStyle { RANGED, MELEE }

/** Combat behavior lives outside the director, persistence and collision systems. */
interface HeroCombatKit {
    val ultimateRadius:Float get()=220f
    val ultimateRange:Float get()=350f
    val ultimateIsRush:Boolean get()=false
    val speedMultiplier:Float get()=1f
    val damageReceived:Float get()=1f
    val gadgetDuration:Float get()=8f
    val gadgetCooldown:Float get()=0f
    val style: AttackStyle
    val ultimateCharge: Float
    val hyperCharge: Float
    val hyperLeft: Float
    fun tick(dt: Float)
    fun damage(amount: Float)
    fun gadget(): Boolean
    fun attack(dx: Float,dy: Float,held: Float): Boolean
    fun ultimate(px: Float,py: Float): Boolean
    fun hyper(): Boolean
}

class SpiderCombatKit(private val engine: RiftEngine): HeroCombatKit {
    override val ultimateRadius get()=220f+engine.rank(Upgrade.SPECIAL)*35f
    override var style=AttackStyle.RANGED;private set
    override var ultimateCharge=if(engine.tutorial)100f else 0f;private set
    override var hyperCharge=if(engine.tutorial)100f else 0f;private set
    override var hyperLeft=0f;private set
    override fun tick(dt: Float) {hyperLeft=max(0f,hyperLeft-dt)}
    override fun damage(amount: Float) {
        ultimateCharge=min(100f,ultimateCharge+amount*100/engine.character.ultCost)
        if(hyperLeft<=0)hyperCharge=min(100f,hyperCharge+amount*100/engine.character.hyperCost)
    }
    override fun gadget(): Boolean {style=if(style==AttackStyle.RANGED)AttackStyle.MELEE else AttackStyle.RANGED;engine.cancelAttack();return true}
    override fun attack(dx: Float,dy: Float,held: Float): Boolean {
        if(style==AttackStyle.RANGED)return engine.fireDirected(dx,dy,hyperLeft>0)
        if(held>=2f) {
            if(engine.meleeCooldown>0||!engine.consumeCharge())return false
            engine.chargedStrike(dx,dy,if(hyperLeft>0)220f else 160f,engine.character.meleeDamage*(if(hyperLeft>0)3.6f else 2.8f)*(1f+engine.rank(Upgrade.DAMAGE)*.25f))
            return true
        }
        return engine.directedMelee(dx,dy,if(hyperLeft>0)1.3f else 1f,if(hyperLeft>0)1.4f else 1f)
    }
    override fun ultimate(px: Float,py: Float): Boolean {
        if(ultimateCharge<100f&&!engine.tutorial)return false
        ultimateCharge=0f
        engine.areaStrike(px,py,220f+engine.rank(Upgrade.SPECIAL)*35f,90f,3.5f)
        if(engine.tutorial)engine.advanceTutorialUltimate()
        return true
    }
    override fun hyper(): Boolean {
        if(hyperLeft>0||hyperCharge<100f&&!engine.tutorial)return false
        hyperCharge=0f;hyperLeft=15f;return true
    }
}
