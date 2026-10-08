package com.example.marvellobby

import com.example.marvellobby.rift.engine.*
import org.junit.Assert.*
import org.junit.Test

class SpiderCombatKitTest {
    private fun ready()=RiftEngine(314).apply {repeat(100){step()}}
    @Test fun autoAimConsumesOneChargeAndModeSwitchMakesNormalMeleeFree() {
        val e=ready();e.spawnEnemy(EnemyKind.TANK,e.x+60,e.y)
        e.beginAttack();assertTrue(e.releaseAttack(true));assertEquals(2,e.webCharges)
        assertTrue(e.gadget());assertEquals(AttackStyle.MELEE,e.kit.style)
        e.beginAttack();assertTrue(e.releaseAttack(true));assertEquals(2,e.webCharges)
        assertTrue(e.gadget());assertEquals(AttackStyle.RANGED,e.kit.style)
    }
    @Test fun chargedStrikeConsumesSharedChargeAndRespectsCooldown() {
        val e=ready();e.gadget();val x=e.x
        e.beginAttack();e.aim(1f,0f);repeat(121){e.step()}
        assertTrue(e.releaseAttack());assertTrue(e.x>x+140);assertEquals(2,e.webCharges)
        assertFalse(e.kit.attack(1f,0f,2f));assertEquals(2,e.webCharges)
    }
    @Test fun ultimateUsesChosenAreaAndDoesNotChargeItself() {
        val e=ready();assertFalse(e.kit.ultimate(e.x,e.y));e.kit.damage(400f)
        val target=e.spawnEnemy(EnemyKind.TANK,e.x+300,e.y)!!;val hp=target.hp
        e.aimUltimate(1f,0f);assertTrue(e.releaseUltimate())
        assertTrue(target.hp<hp);assertEquals(3.5f,target.slow,0f)
        assertEquals(0f,e.kit.ultimateCharge,0f);assertEquals(e.x+350,e.explosionX,0f)
    }
    @Test fun hyperSpreadCannotTripleHitOneTargetAndModesRemainFree() {
        val e=ready();assertFalse(e.hyper());e.kit.damage(1000f);assertTrue(e.hyper())
        val target=e.spawnEnemy(EnemyKind.TANK,e.x+70,e.y)!!;target.speed=0f;target.hp=1000f;target.maxHp=1000f
        e.beginAttack();e.aim(1f,0f);assertTrue(e.releaseAttack())
        assertEquals(3,e.projectiles.count{it.alive});assertEquals(2,e.webCharges)
        repeat(12){e.step()};assertEquals(977f,target.hp,.01f)
        assertTrue(e.gadget());assertTrue(e.gadget());assertTrue(e.kit.hyperLeft>14f)
        repeat(910){e.step()};assertEquals(0f,e.kit.hyperLeft,0f)
    }
    @Test fun pauseCancelsHeldAttackWithoutSpendingResource() {
        val e=ready();e.beginAttack();e.aim(1f,0f);e.pause()
        assertFalse(e.releaseAttack());assertEquals(3,e.webCharges);assertEquals(0f,e.attackHeld,0f)
    }
    @Test fun explosionUpgradeAlsoRespectsTheHyperVolleyHitLimit() {
        val e=ready();e.upgrades[Upgrade.EXPLOSION.ordinal]=1;e.kit.damage(1000f);e.hyper()
        val targets=listOf(-28f,0f,28f).map{dy->e.spawnEnemy(EnemyKind.TANK,e.x+70,e.y+dy)!!.apply{speed=0f;hp=1000f;maxHp=1000f}}
        e.beginAttack();e.aim(1f,0f);assertTrue(e.releaseAttack());repeat(12){e.step()}
        targets.forEach{assertTrue("A volley dealt repeated damage",it.hp>=977f)}
        assertTrue(targets.any{it.hp<1000f})
    }
}
