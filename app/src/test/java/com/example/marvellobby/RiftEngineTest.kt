package com.example.marvellobby

import com.example.marvellobby.rift.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class RiftEngineTest {
    @Test fun aimIsIndependentReleaseConsumesChargesAndCancellationDoesNotFire() {
        val e=readyEngine(91);e.moveX=-1f;e.aim(1f,0f)
        val x=e.x;e.step();assertTrue(e.x<x);assertTrue(e.aiming);assertTrue(e.projectiles.none{it.alive})
        assertTrue(e.releaseWeb());assertEquals(2,e.webCharges)
        assertTrue(e.projectiles.first{it.alive}.vx>0);assertFalse(e.aiming)
        repeat(20){e.step()};e.aim(1f,0f);e.clearInput();assertFalse(e.releaseWeb());assertEquals(2,e.webCharges)
        repeat(110){e.step()};assertEquals(3,e.webCharges)
    }
    @Test fun emptyAmmoRejectsAndMeleeDoesNotConsumeAmmo() {
        val e=readyEngine(92)
        repeat(3){e.aim(0f,-1f);assertTrue(e.releaseWeb());repeat(20){e.step()}}
        assertEquals(0,e.webCharges);e.aim(1f,0f);assertFalse(e.releaseWeb())
        val enemy=e.spawnEnemy(EnemyKind.TANK,e.x,e.y-45)!!;val hp=enemy.hp
        assertTrue(e.melee());assertTrue(enemy.hp<hp);assertEquals(0,e.webCharges);assertFalse(e.melee())
        repeat(18){e.step()};assertTrue(e.melee());assertEquals(2,e.meleeCombo)
    }
    @Test fun webAndExplosionRefreshSlowWithoutStackingMovementPenalty() {
        val e=readyEngine(93);e.upgrades[Upgrade.EXPLOSION.ordinal]=1
        val target=e.spawnEnemy(EnemyKind.TANK,e.x+65,e.y)!!
        val nearby=e.spawnEnemy(EnemyKind.TANK,e.x+65,e.y+45)!!
        e.aim(1f,0f);e.releaseWeb();repeat(8){e.step()}
        assertTrue(target.slow>0);assertTrue(nearby.slow>0)
        val slowBefore=target.slow;repeat(20){e.step()};e.upgrades[Upgrade.SLOW.ordinal]=1;e.aim(1f,0f);e.releaseWeb();repeat(4){e.step()}
        assertTrue(target.slow>slowBefore-.5f);assertTrue(target.slow<=3.5f)
        val stickyDuration=target.slow;assertTrue(e.special());assertEquals(stickyDuration,target.slow,0f)
    }
    @Test fun tutorialRequiresActionsAndNeverSpawnsHordes() {
        val e=RiftEngine(1,tutorial=true);repeat(120){e.step()};assertEquals(0,e.tutorialStep)
        e.moveX=1f;repeat(25){e.step()};assertEquals(1,e.tutorialStep)
        e.aim(1f,0f);assertTrue(e.releaseWeb());assertEquals(2,e.tutorialStep)
        assertTrue(e.melee());assertEquals(3,e.tutorialStep)
        assertTrue(e.special());assertEquals(4,e.tutorialStep)
        e.tutorialNext();e.tutorialNext();assertEquals(6,e.tutorialStep)
        assertTrue(e.enemies.none{it.alive});assertEquals(0,e.kills)
    }
    @Test fun waveTransitionFreezesCombatClockAndCannotSpendAmmo() {
        val e=readyEngine(94)
        while(e.time<29.99f){e.enemies.forEach{it.alive=false};e.step()}
        e.step();assertEquals(2,e.wave);assertTrue(e.transition>0)
        val time=e.time;e.aim(1f,0f);assertFalse(e.releaseWeb());repeat(30){e.step()};assertEquals(time,e.time,0f)
        assertEquals(3,e.webCharges)
    }
    private fun readyEngine(seed: Int)=RiftEngine(seed).also { repeat(100){_->it.step()} }
    @Test fun normalizedMovementAndPause() {
        val straight=readyEngine(8);val diagonal=readyEngine(8)
        straight.moveX=1f;diagonal.moveX=1f;diagonal.moveY=1f
        repeat(30) { straight.step();diagonal.step() }
        assertEquals(straight.x-900f,sqrt((diagonal.x-900f)*(diagonal.x-900f)+(diagonal.y-900f)*(diagonal.y-900f)),0.05f)
        diagonal.pause();val x=diagonal.x;repeat(60) { diagonal.step() };assertEquals(x,diagonal.x,0f)
        diagonal.resume();assertEquals(RunPhase.PLAYING,diagonal.phase)
    }
    @Test fun dashHasCooldownAndIgnoresContactDuringItsWindow() {
        val engine=readyEngine(1);engine.spawnEnemy(EnemyKind.MELEE,900f,900f)
        assertTrue(engine.dash());assertFalse(engine.dash());engine.step();assertEquals(100f,engine.hp,0f)
        assertTrue(engine.dashCooldown>0);assertEquals(AnimationState.DASH,engine.animation)
    }
    @Test fun obstacleStopsMovementAndDiagonalSlidesAlongEdge() {
        val engine=readyEngine(2);engine.moveY=-1f
        repeat(160) { engine.step() }
        assertTrue(engine.y>=527f);val x=engine.x
        engine.moveX=1f;repeat(15) { engine.step() };assertTrue(engine.x>x)
    }
    @Test fun repeatedSeedProducesSameRunAndBossHasTelegraph() {
        val a=readyEngine(123);val b=readyEngine(123)
        repeat(1200) { a.aim(1f,0f);a.releaseWeb();b.aim(1f,0f);b.releaseWeb();a.step();b.step()
            if(a.phase==RunPhase.UPGRADE) { val choice=a.choices.first();assertEquals(a.choices,b.choices);a.choose(choice);b.choose(choice) } }
        assertEquals(a.result(),b.result());assertEquals(a.enemies.map { it.kind to it.x },b.enemies.map { it.kind to it.x })
        val boss=readyEngine(7);val e=boss.spawnEnemy(EnemyKind.BOSS,1200f,1200f)!!
        repeat(65) { boss.step() };assertTrue(e.windup>0);assertTrue(boss.projectiles.none { it.alive&&!it.friendly })
        repeat(85) { boss.step() };assertTrue(boss.projectiles.any { it.alive&&!it.friendly })
    }
    @Test fun specialKillsGrantXpLevelChoicesAndUpgradesWork() {
        val engine=readyEngine(4)
        repeat(4) { engine.spawnEnemy(EnemyKind.MELEE,910f,910f) }
        assertTrue(engine.special());assertFalse(engine.special());engine.step()
        assertEquals(4,engine.kills);repeat(45){engine.step()};assertEquals(RunPhase.UPGRADE,engine.phase);assertEquals(3,engine.choices.distinct().size)
        val time=engine.time;engine.step();assertEquals(time,engine.time,0f)
        val choice=engine.choices.first();assertTrue(engine.choose(choice));assertEquals(1,engine.rank(choice));assertFalse(engine.choose(choice))
    }
    @Test fun deathEndsAndScoreExplainsMoreThanKills() {
        val e=readyEngine(7);repeat(10) { e.spawnEnemy(EnemyKind.TANK,900f,900f) }
        repeat(1000) { e.step() };assertEquals(RunPhase.FINISHED,e.phase);assertEquals(0f,e.hp,0f);assertFalse(e.result().extracted)
        assertEquals(2530,RiftRules.score(10f,5,1,1,400f,40))
    }
}
