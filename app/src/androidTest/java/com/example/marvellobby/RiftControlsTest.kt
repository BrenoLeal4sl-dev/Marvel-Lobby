package com.example.marvellobby

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.marvellobby.rift.engine.*
import com.example.marvellobby.rift.presentation.RiftActivity
import com.example.marvellobby.rift.render.RiftGameView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.flow.first

@RunWith(AndroidJUnit4::class)
class RiftControlsTest {
    @Test fun characterSelectionInspectsAllKitsAndPersistsTheChosenHero() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val owner="hero-selection-${java.util.UUID.randomUUID()}"
        val preferences=com.example.marvellobby.data.local.PreferencesStore(context)
        kotlinx.coroutines.runBlocking{preferences.finishRiftTutorial(owner)}
        ActivityScenario.launch<RiftActivity>(Intent(context,RiftActivity::class.java).putExtra("language","pt").putExtra("owner",owner)).use{scenario->
            fun awaitTag(tag:String){val until=SystemClock.uptimeMillis()+12000;var found=false
                while(!found&&SystemClock.uptimeMillis()<until){scenario.onActivity{found=all(it.window.decorView).any{v->v.tag==tag&&v.isEnabled&&v.width>0}};if(!found)SystemClock.sleep(50)}
                assertTrue("Missing $tag",found)
            }
            awaitTag("rift:play")
            scenario.onActivity{a->all(a.window.decorView).filterIsInstance<Button>().first{it.text=="Personagens"}.performClick()}
            for(hero in RiftCharacters.all){awaitTag("rift:inspect:${hero.key}");scenario.onActivity{a->all(a.window.decorView).first{it.tag=="rift:inspect:${hero.key}"}.performClick()};awaitTag("rift:preview:${hero.key}")}
            scenario.onActivity{a->val root=a.window.decorView;val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(bitmap));File(context.filesDir,"qa/rift3-selection.png").apply{parentFile!!.mkdirs()}.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
            scenario.onActivity{a->all(a.window.decorView).filterIsInstance<android.widget.ScrollView>().first().fullScroll(View.FOCUS_DOWN)}
            var visible=false;val scrollDeadline=SystemClock.uptimeMillis()+5000
            while(!visible&&SystemClock.uptimeMillis()<scrollDeadline){scenario.onActivity{a->val select=all(a.window.decorView).first{it.tag=="rift:select"};val rect=android.graphics.Rect();visible=select.getGlobalVisibleRect(rect)&&rect.height()>=select.height-2};if(!visible)SystemClock.sleep(50)}
            assertTrue("Select button is clipped",visible)
            scenario.onActivity{a->all(a.window.decorView).first{it.tag=="rift:select"}.performClick()}
            awaitTag("rift:play")
            kotlinx.coroutines.runBlocking{kotlinx.coroutines.withTimeout(5000){preferences.riftHero(owner).first{it=="doctor-strange"}}}
        }
    }
    @Test fun sixHeroTouchControlsSupportAutoAimGadgetUltimateAndHyper() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        ActivityScenario.launch<RiftActivity>(Intent(context,RiftActivity::class.java).putExtra("language","pt").putExtra("owner","kit-touch-test")).use{scenario->
            SystemClock.sleep(1500)
            for(hero in RiftCharacters.all)scenario.onActivity{a->
                val engine=RiftEngine(315,hero).apply{repeat(100){step()}}
                val view=RiftGameView(a,engine,"pt"){}
                a.setContentView(view);view.layout(0,0,1080,2000)
                val attack=rect(view,"attack");val gadget=rect(view,"gadget");val ult=rect(view,"special");val hyper=rect(view,"hyper")
                fun tap(r:RectF){val point=listOf(r.centerX() to r.centerY());touch(view,MotionEvent.ACTION_DOWN,point);touch(view,MotionEvent.ACTION_UP,point)}
                tap(attack)
                assertTrue(hero.name,engine.projectiles.any{it.alive}||engine.meleeFlash>0)
                repeat(40){engine.step()};tap(gadget)
                assertTrue(hero.name,engine.kit.gadgetCooldown>0||engine.kit.style!=AttackStyle.RANGED||hero.key=="thor")
                engine.kit.damage(hero.hyperCost);tap(hyper);assertTrue(hero.name,engine.kit.hyperLeft>0)
                val aimed=listOf(ult.centerX()+ult.width()*.4f to ult.centerY())
                touch(view,MotionEvent.ACTION_DOWN,aimed);assertTrue(engine.ultimateAiming)
                touch(view,MotionEvent.ACTION_UP,aimed);assertFalse(engine.ultimateAiming);assertEquals(0f,engine.kit.ultimateCharge,0f)
                touch(view,MotionEvent.ACTION_DOWN,listOf(attack.centerX()+attack.width()*.4f to attack.centerY()))
                touch(view,MotionEvent.ACTION_CANCEL,listOf(attack.centerX() to attack.centerY()));assertFalse(engine.aiming)
                val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(bitmap))
                File(context.filesDir,"qa/rift3-${hero.key}.png").apply{parentFile!!.mkdirs()}.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
        }
    }
    @Test fun firstVisitTutorialPersistsAndReplayDoesNotStartCompetitiveRun() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val owner="rift-tutorial-${java.util.UUID.randomUUID()}"
        ActivityScenario.launch<RiftActivity>(Intent(context,RiftActivity::class.java).putExtra("language","pt").putExtra("owner",owner)).use {scenario->
            fun awaitView(predicate:(List<View>)->Boolean) {
                var ready=false;val deadline=SystemClock.uptimeMillis()+12000
                while(!ready&&SystemClock.uptimeMillis()<deadline){scenario.onActivity{ready=predicate(all(it.window.decorView))};if(!ready)SystemClock.sleep(100)}
                assertTrue("Arena view did not become ready",ready)
            }
            fun capture(name:String) {inst.waitForIdleSync();SystemClock.sleep(400);val bitmap=inst.uiAutomation.takeScreenshot();assertNotNull(bitmap)
                File(context.filesDir,"qa/$name.png").apply{parentFile!!.mkdirs()}.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
            awaitView {it.filterIsInstance<Button>().any{b->b.tag=="rift:play"&&b.isEnabled}}
            scenario.onActivity {a->val play=all(a.window.decorView).filterIsInstance<Button>().first{it.tag=="rift:play"};val visible=android.graphics.Rect();assertTrue(play.getGlobalVisibleRect(visible));assertTrue(visible.height()>=play.height-2)}
            capture("rift-v2-menu")
            scenario.onActivity{a->all(a.window.decorView).filterIsInstance<Button>().first{it.tag=="rift:play"}.performClick()}
            awaitView {it.any{v->v is RiftGameView}}
            capture("rift-v2-tutorial")
            scenario.onActivity {a->
                val v=all(a.window.decorView).filterIsInstance<RiftGameView>().single();v.stopFrames();assertTrue(v.engine.tutorial)
                val joy=rect(v,"joystick");val aim=rect(v,"attack")
                touch(v,MotionEvent.ACTION_DOWN,listOf(joy.right to joy.centerY()));repeat(25){v.engine.step()}
                touch(v,MotionEvent.ACTION_UP,listOf(joy.right to joy.centerY()));assertEquals(1,v.engine.tutorialStep)
                val aimed=listOf(aim.centerX()+aim.width()*.4f to aim.centerY());touch(v,MotionEvent.ACTION_DOWN,aimed);touch(v,MotionEvent.ACTION_UP,aimed)
                assertEquals(2,v.engine.tutorialStep)
                val melee=rect(v,"gadget");touch(v,MotionEvent.ACTION_DOWN,listOf(melee.centerX() to melee.centerY()));touch(v,MotionEvent.ACTION_UP,listOf(melee.centerX() to melee.centerY()))
                touch(v,MotionEvent.ACTION_DOWN,aimed);touch(v,MotionEvent.ACTION_UP,aimed)
                assertEquals(3,v.engine.tutorialStep)
                val special=rect(v,"special");touch(v,MotionEvent.ACTION_DOWN,listOf(special.centerX() to special.centerY()));touch(v,MotionEvent.ACTION_UP,listOf(special.centerX() to special.centerY()))
                assertEquals(4,v.engine.tutorialStep)
                val next=rect(v,"tutorialNext")
                repeat(2){touch(v,MotionEvent.ACTION_DOWN,listOf(next.centerX() to next.centerY()));touch(v,MotionEvent.ACTION_UP,listOf(next.centerX() to next.centerY()))}
                assertEquals(6,v.engine.tutorialStep);v.startFrames()
            }
            awaitView {it.any{v->v is RiftGameView&&!v.engine.tutorial}}
            assertTrue(kotlinx.coroutines.runBlocking {com.example.marvellobby.data.local.PreferencesStore(context).riftTutorial(owner).first()})
            scenario.onActivity {a->
                val v=all(a.window.decorView).filterIsInstance<RiftGameView>().single();v.stopFrames();repeat(100){v.engine.step()}
                repeat(4){i->v.engine.enemies[i].apply {alive=true;serial=i+100;kind=EnemyKind.MELEE;x=v.engine.x+10;y=v.engine.y+10;hp=48f;maxHp=48f;radius=17f}}
                assertTrue(v.engine.special());v.engine.step();assertTrue(v.engine.levelUpLeft>0);v.startFrames()
            }
            awaitView {it.filterIsInstance<Button>().any{b->b.text=="Escolher"}}
            capture("rift-v2-upgrade")
            scenario.onActivity{a->all(a.window.decorView).filterIsInstance<Button>().first{it.text=="Escolher"}.performClick()}
            awaitView {it.filterIsInstance<Button>().none{b->b.text=="Escolher"}}
            scenario.onActivity {a->a.onBackPressedDispatcher.onBackPressed()}
            awaitView {it.filterIsInstance<Button>().any{b->b.text=="Sair da partida"}}
            scenario.onActivity {a->all(a.window.decorView).filterIsInstance<Button>().first{it.text=="Sair da partida"}.performClick()}
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Sair")).perform(androidx.test.espresso.action.ViewActions.click())
            awaitView {it.filterIsInstance<Button>().any{b->b.tag=="rift:play"&&b.isEnabled}}
            scenario.onActivity {a->all(a.window.decorView).filterIsInstance<Button>().first{it.text=="Configurações da arena"}.performClick()}
            awaitView {it.filterIsInstance<Button>().any{b->b.text=="Repetir tutorial"}}
            scenario.onActivity {a->all(a.window.decorView).filterIsInstance<Button>().first{it.text=="Repetir tutorial"}.performClick()}
            awaitView {it.any{v->v is RiftGameView&&v.engine.tutorial}}
        }
    }
    private fun all(root: View): List<View> = listOf(root)+if(root is ViewGroup)(0 until root.childCount).flatMap { all(root.getChildAt(it)) }else emptyList()
    private fun rect(view: RiftGameView,name: String)=RiftGameView::class.java.getDeclaredField(name).apply { isAccessible=true }.get(view) as RectF
    private fun touch(view: View,action: Int,points: List<Pair<Float,Float>>) {
        val now=SystemClock.uptimeMillis()
        val props=points.mapIndexed { i,_->MotionEvent.PointerProperties().apply { id=i;toolType=MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
        val coords=points.map { MotionEvent.PointerCoords().apply { x=it.first;y=it.second;pressure=1f;size=1f } }.toTypedArray()
        val event=MotionEvent.obtain(now,now,action,points.size,props,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
        view.dispatchTouchEvent(event);event.recycle()
    }
    @Test fun multitouchMovementAttackDashPauseRecreateAndRestart() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val context=inst.targetContext
        kotlinx.coroutines.runBlocking { com.example.marvellobby.data.local.PreferencesStore(context).finishRiftTutorial("rift-test") }
        ActivityScenario.launch<RiftActivity>(Intent(context,RiftActivity::class.java).putExtra("language","pt").putExtra("owner","rift-test")).use { scenario ->
            var ready=false;val deadline=SystemClock.uptimeMillis()+10000
            while(!ready && SystemClock.uptimeMillis()<deadline) {scenario.onActivity { a->ready=all(a.window.decorView).filterIsInstance<Button>().any { it.tag=="rift:play" && it.isEnabled } };if(!ready)SystemClock.sleep(100)}
            assertTrue("Arena lobby did not become ready",ready)
            scenario.onActivity { a->all(a.window.decorView).filterIsInstance<Button>().first { it.tag=="rift:play" }.performClick() }
            var playing=false
            while(!playing && SystemClock.uptimeMillis()<deadline) {scenario.onActivity { a->playing=all(a.window.decorView).any { it is RiftGameView } };if(!playing)SystemClock.sleep(100)}
            assertTrue(playing)
            inst.waitForIdleSync()
            var initialTick=0
            scenario.onActivity { a ->
                val v=all(a.window.decorView).filterIsInstance<RiftGameView>().single();v.stopFrames();repeat(100){v.engine.step()}
                val joy=rect(v,"joystick");val attack=rect(v,"attack")
                val points=listOf(joy.centerX()+joy.width()*0.45f to joy.centerY(),attack.centerX()+attack.width()*.4f to attack.centerY())
                touch(v,MotionEvent.ACTION_DOWN,points.take(1))
                touch(v,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),points)
                assertTrue(v.engine.moveX>0.7f);assertTrue(v.engine.aiming)
                val x=v.engine.x;repeat(20){v.engine.step()};assertTrue(v.engine.x>x);assertTrue(v.engine.projectiles.none { it.alive&&it.friendly })
                touch(v,MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),points)
                assertFalse(v.engine.aiming);assertEquals(2,v.engine.webCharges);assertTrue(v.engine.projectiles.any {it.alive&&it.friendly&&it.vx>0});assertTrue(v.engine.moveX>0)
                val dash=rect(v,"dash");touch(v,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(points[0],dash.centerX() to dash.centerY()))
                assertTrue(v.engine.dashLeft>0);touch(v,MotionEvent.ACTION_CANCEL,points)
                assertEquals(0f,v.engine.moveX,0f);assertFalse(v.engine.aiming)
                initialTick=v.engine.tick;v.startFrames()
            }
            SystemClock.sleep(700)
            scenario.onActivity { a->val v=all(a.window.decorView).filterIsInstance<RiftGameView>().single();assertTrue(v.engine.tick>initialTick);assertTrue(v.averageFrameMs>0) }
            scenario.moveToState(Lifecycle.State.CREATED)
            var paused=0
            scenario.onActivity { a->val v=all(a.window.decorView).filterIsInstance<RiftGameView>().single();assertEquals(RunPhase.PAUSED,v.engine.phase);paused=v.engine.tick }
            SystemClock.sleep(150);scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { a->assertEquals(paused,all(a.window.decorView).filterIsInstance<RiftGameView>().single().engine.tick) }
            val screenshot=inst.uiAutomation.takeScreenshot();assertNotNull(screenshot)
            File(context.filesDir,"qa/rift-game.png").apply { parentFile!!.mkdirs() }.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) };screenshot.recycle()
            scenario.recreate()
            scenario.onActivity { a->
                val v=all(a.window.decorView).filterIsInstance<RiftGameView>().single();assertEquals(paused,v.engine.tick)
                all(a.window.decorView).filterIsInstance<Button>().first { it.text.toString()=="Continuar" }.performClick()
                assertEquals(RunPhase.PLAYING,v.engine.phase)
            }
        }
    }
}
