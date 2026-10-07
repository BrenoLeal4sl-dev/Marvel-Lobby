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

@RunWith(AndroidJUnit4::class)
class RiftControlsTest {
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
        ActivityScenario.launch<RiftActivity>(Intent(context,RiftActivity::class.java).putExtra("language","pt").putExtra("owner","rift-test")).use { scenario ->
            var ready=false;val deadline=SystemClock.uptimeMillis()+10000
            while(!ready && SystemClock.uptimeMillis()<deadline) {scenario.onActivity { a->ready=all(a.window.decorView).filterIsInstance<Button>().any { it.tag=="rift:play" && it.isEnabled } };if(!ready)SystemClock.sleep(100)}
            assertTrue("Arena lobby did not become ready",ready)
            scenario.onActivity { a->all(a.window.decorView).filterIsInstance<Button>().first { it.text.toString()=="ENTRAR NA FENDA" }.performClick() }
            var playing=false
            while(!playing && SystemClock.uptimeMillis()<deadline) {scenario.onActivity { a->playing=all(a.window.decorView).any { it is RiftGameView } };if(!playing)SystemClock.sleep(100)}
            assertTrue(playing)
            inst.waitForIdleSync()
            var initialTick=0
            scenario.onActivity { a ->
                val v=all(a.window.decorView).filterIsInstance<RiftGameView>().single();v.stopFrames()
                val joy=rect(v,"joystick");val attack=rect(v,"attack")
                val points=listOf(joy.centerX()+joy.width()*0.45f to joy.centerY(),attack.centerX() to attack.centerY())
                touch(v,MotionEvent.ACTION_DOWN,points.take(1))
                touch(v,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),points)
                assertTrue(v.engine.moveX>0.7f);assertTrue(v.engine.attacking)
                val x=v.engine.x;repeat(20){v.engine.step()};assertTrue(v.engine.x>x);assertTrue(v.engine.projectiles.any { it.alive&&it.friendly })
                touch(v,MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),points)
                assertFalse(v.engine.attacking);assertTrue(v.engine.moveX>0)
                val dash=rect(v,"dash");touch(v,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(points[0],dash.centerX() to dash.centerY()))
                assertTrue(v.engine.dashLeft>0);touch(v,MotionEvent.ACTION_CANCEL,points)
                assertEquals(0f,v.engine.moveX,0f);assertFalse(v.engine.attacking)
                initialTick=v.engine.tick;v.startFrames()
            }
            SystemClock.sleep(700)
            scenario.onActivity { a->val v=all(a.window.decorView).filterIsInstance<RiftGameView>().single();assertTrue(v.engine.tick>initialTick);assertTrue(v.averageFrameMs>0) }
            val screenshot=inst.uiAutomation.takeScreenshot();assertNotNull(screenshot)
            File(context.filesDir,"qa/rift-game.png").apply { parentFile!!.mkdirs() }.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) };screenshot.recycle()
            scenario.moveToState(Lifecycle.State.CREATED)
            var paused=0
            scenario.onActivity { a->val v=all(a.window.decorView).filterIsInstance<RiftGameView>().single();assertEquals(RunPhase.PAUSED,v.engine.phase);paused=v.engine.tick }
            SystemClock.sleep(150);scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { a->assertEquals(paused,all(a.window.decorView).filterIsInstance<RiftGameView>().single().engine.tick) }
            scenario.recreate()
            scenario.onActivity { a->
                val v=all(a.window.decorView).filterIsInstance<RiftGameView>().single();assertEquals(paused,v.engine.tick)
                all(a.window.decorView).filterIsInstance<Button>().first { it.text.toString()=="Continuar" }.performClick()
                assertEquals(RunPhase.PLAYING,v.engine.phase)
            }
        }
    }
}
