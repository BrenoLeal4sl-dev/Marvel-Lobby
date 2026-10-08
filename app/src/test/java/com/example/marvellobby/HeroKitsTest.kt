package com.example.marvellobby

import com.example.marvellobby.rift.engine.*
import org.junit.Assert.*
import org.junit.Test

class HeroKitsTest {
    private fun ready(h:CharacterDefinition)=RiftEngine(712,h).apply{repeat(100){step()}}
    @Test fun eachKitHasWorkingAttacksAbilitiesAndHonestResults() {
        for(h in RiftCharacters.all) {
            val e=ready(h);val target=e.spawnEnemy(EnemyKind.TANK,e.x+45,e.y)!!
            target.speed=0f;target.hp=1000f;target.maxHp=1000f
            e.beginAttack();e.aim(1f,0f);assertTrue(h.name,e.releaseAttack())
            repeat(40){e.step()};assertTrue(h.name,target.hp<1000f)
            assertEquals(h.key,e.result().character);assertEquals(h.health,e.maxHp,0f)
            assertTrue(h.name,e.gadget());e.kit.damage(h.hyperCost)
            assertTrue(h.name,e.hyper());assertTrue(e.kit.hyperLeft>0f)
            e.aimUltimate(.5f,0f);assertTrue(h.name,e.releaseUltimate());assertEquals(0f,e.kit.ultimateCharge,0f)
            e.clearInput();assertFalse(e.aiming);assertFalse(e.ultimateAiming)
        }
    }
    @Test fun meleeHeroesNeverCreateArtificialProjectiles() {
        for(h in listOf(RiftCharacters.hulk,RiftCharacters.wolverine)) {
            val e=ready(h);e.beginAttack();assertTrue(e.releaseAttack(true))
            assertEquals(3,e.webCharges);assertTrue(e.projectiles.none{it.alive})
            assertEquals(AttackStyle.MELEE,e.kit.style)
        }
    }
    @Test fun ironPrecisionPiercesAndHulkHyperAddsShockwaveBeyondMeleeReach() {
        val iron=ready(RiftCharacters.iron);assertTrue(iron.gadget());iron.beginAttack();iron.aim(1f,0f);assertTrue(iron.releaseAttack())
        assertEquals(1,iron.projectiles.first{it.alive}.pierce)
        val hulk=ready(RiftCharacters.hulk);val target=hulk.spawnEnemy(EnemyKind.TANK,hulk.x+155,hulk.y)!!
        val hp=target.hp;hulk.kit.damage(1450f);hulk.hyper();hulk.beginAttack();hulk.aim(1f,0f);hulk.releaseAttack()
        assertTrue(target.hp<hp);assertTrue(hulk.kit.damageReceived<1f)
    }
    @Test fun portalRejectsBlockedDestinationWithoutSpendingCooldown() {
        val e=ready(RiftCharacters.strange);assertTrue(e.reposition(570f,460f))
        e.aim(0f,1f);assertFalse(e.gadget());assertEquals(0f,e.kit.gadgetCooldown,0f)
        e.aim(1f,0f);assertTrue(e.gadget());assertTrue(e.kit.gadgetCooldown>0)
    }
    @Test fun wolverineRecoversAndThorCanSwitchBetweenPhysicalAndThrownHammer() {
        val w=ready(RiftCharacters.wolverine);w.spawnEnemy(EnemyKind.MELEE,w.x,w.y);w.step()
        w.enemies.forEach{it.alive=false};val hp=w.hp;repeat(120){w.step()};assertTrue(w.hp>hp)
        val t=ready(RiftCharacters.thor);assertEquals(AttackStyle.MELEE,t.kit.style);t.gadget()
        t.beginAttack();t.aim(1f,0f);assertTrue(t.releaseAttack());assertEquals(2,t.webCharges)
        assertTrue(t.projectiles.any{it.alive});t.gadget();assertEquals(AttackStyle.MELEE,t.kit.style)
    }
    @Test fun cancelledAimCannotAttackAndChargedLungeCannotPassThroughWalls() {
        val e=ready(RiftCharacters.spider);assertTrue(e.reposition(530f,740f));e.gadget()
        e.beginAttack();e.aim(1f,0f);e.clearInput();assertFalse(e.releaseAttack());assertEquals(3,e.webCharges)
        assertTrue(e.kit.attack(1f,0f,2f));assertTrue(e.x<560f)
    }
    @Test fun physicalAttacksCannotDamageEnemiesThroughAnObstacle() {
        val e=ready(RiftCharacters.thor);e.upgrades[Upgrade.PIERCE.ordinal]=1;assertTrue(e.reposition(900f,412f))
        val enemy=e.spawnEnemy(EnemyKind.TANK,900f,534f)!!;val hp=enemy.hp
        e.beginAttack();e.aim(0f,1f);assertTrue(e.releaseAttack());assertEquals(hp,enemy.hp,0f)
    }
    @Test fun allHeroesCanEngageTheSeededSurvivalDirectorWithTheirOwnResources() {
        for(hero in RiftCharacters.all) {
            val e=RiftEngine(918,hero)
            repeat(3600){tick->
                if(e.phase==RunPhase.UPGRADE)e.choose(e.choices.firstOrNull{it==Upgrade.DAMAGE}?:e.choices.first())
                if(e.phase==RunPhase.PLAYING){
                    val target=e.enemies.filter{it.alive}.minByOrNull{(it.x-e.x)*(it.x-e.x)+(it.y-e.y)*(it.y-e.y)}
                    if(target!=null){
                        val dx=target.x-e.x;val dy=target.y-e.y;val d=kotlin.math.sqrt(dx*dx+dy*dy).coerceAtLeast(1f)
                        val ranged=e.kit.style==AttackStyle.RANGED
                        val factor=if(ranged&&d<230f)-1f else if(ranged&&d<400f)0f else 1f
                        e.moveX=dx/d*factor;e.moveY=dy/d*factor
                        if(tick%20==0){e.beginAttack();e.aim(dx,dy);e.releaseAttack()}
                        if(e.kit.ultimateCharge>=100){e.aimUltimate((dx/e.kit.ultimateRange).coerceIn(-1f,1f),(dy/e.kit.ultimateRange).coerceIn(-1f,1f));e.releaseUltimate()}
                        if(e.kit.hyperCharge>=100)e.hyper()
                    }
                }
                e.step()
            }
            assertTrue("${hero.name} never dealt damage",e.damage>0)
            assertTrue("${hero.name} never defeated an enemy",e.kills>0)
            assertTrue(e.hp in 0f..e.maxHp);assertTrue(e.webCharges in 0..3)
            println("survival-fixture ${hero.key}: ${e.time.toInt()}s, ${e.kills} kills, ${e.damage.toInt()} damage, ${e.hp.toInt()} hp")
        }
    }
}
