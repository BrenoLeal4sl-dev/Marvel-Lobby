package com.example.marvellobby

import com.example.marvellobby.rift.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class RiftEngineTest {
    @Test fun normalizedMovementAndPause() {
        val straight=RiftEngine(8);val diagonal=RiftEngine(8)
        straight.moveX=1f;diagonal.moveX=1f;diagonal.moveY=1f
        repeat(30) { straight.step();diagonal.step() }
        assertEquals(straight.x-900f,sqrt((diagonal.x-900f)*(diagonal.x-900f)+(diagonal.y-900f)*(diagonal.y-900f)),0.05f)
        diagonal.pause();val x=diagonal.x;repeat(60) { diagonal.step() };assertEquals(x,diagonal.x,0f)
        diagonal.resume();assertEquals(RunPhase.PLAYING,diagonal.phase)
    }
    @Test fun dashHasCooldownAndIgnoresContactDuringItsWindow() {
        val engine=RiftEngine(1);engine.spawnEnemy(EnemyKind.MELEE,900f,900f)
        assertTrue(engine.dash());assertFalse(engine.dash());engine.step();assertEquals(100f,engine.hp,0f)
        assertTrue(engine.dashCooldown>0);assertEquals(AnimationState.DASH,engine.animation)
    }
    @Test fun obstacleStopsMovementAndDiagonalSlidesAlongEdge() {
        val engine=RiftEngine(2);engine.moveY=-1f
        repeat(160) { engine.step() }
        assertTrue(engine.y>=527f);val x=engine.x
        engine.moveX=1f;repeat(15) { engine.step() };assertTrue(engine.x>x)
    }
    @Test fun repeatedSeedProducesSameRunAndBossHasTelegraph() {
        val a=RiftEngine(123);val b=RiftEngine(123)
        repeat(1200) { a.attacking=true;b.attacking=true;a.step();b.step()
            if(a.phase==RunPhase.UPGRADE) { val choice=a.choices.first();assertEquals(a.choices,b.choices);a.choose(choice);b.choose(choice) } }
        assertEquals(a.result(),b.result());assertEquals(a.enemies.map { it.kind to it.x },b.enemies.map { it.kind to it.x })
        val boss=RiftEngine(7);val e=boss.spawnEnemy(EnemyKind.BOSS,1200f,1200f)!!
        repeat(65) { boss.step() };assertTrue(e.windup>0);assertTrue(boss.projectiles.none { it.alive&&!it.friendly })
        repeat(85) { boss.step() };assertTrue(boss.projectiles.any { it.alive&&!it.friendly })
    }
    @Test fun specialKillsGrantXpLevelChoicesAndUpgradesWork() {
        val engine=RiftEngine(4)
        repeat(4) { engine.spawnEnemy(EnemyKind.MELEE,910f,910f) }
        assertTrue(engine.special());assertFalse(engine.special());engine.step()
        assertEquals(4,engine.kills);assertEquals(RunPhase.UPGRADE,engine.phase);assertEquals(3,engine.choices.distinct().size)
        val time=engine.time;engine.step();assertEquals(time,engine.time,0f)
        val choice=engine.choices.first();assertTrue(engine.choose(choice));assertEquals(1,engine.rank(choice));assertFalse(engine.choose(choice))
    }
    @Test fun deathEndsAndScoreExplainsMoreThanKills() {
        val e=RiftEngine(7);repeat(10) { e.spawnEnemy(EnemyKind.TANK,900f,900f) }
        repeat(1000) { e.step() };assertEquals(RunPhase.FINISHED,e.phase);assertEquals(0f,e.hp,0f);assertFalse(e.result().extracted)
        assertEquals(2530,RiftRules.score(10f,5,1,1,400f,40))
    }
}
