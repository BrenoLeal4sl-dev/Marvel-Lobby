package com.example.marvellobby

import android.graphics.Rect
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.marvellobby.data.local.RemoteAccount
import com.example.marvellobby.data.repository.UserProfile
import com.example.marvellobby.presentation.*
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Synthetic local identity has no tokens, so these checks cannot create remote social data. */
@RunWith(AndroidJUnit4::class)
class CommunityUiTest {
    @Test fun expiredSessionOffersOnlineSignInWithoutDeletingTheCachedAccount()=runBlocking<Unit> {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as MarvelApplication
        val user=UserProfile("Session test","session@example.invalid",username="session_hero",id=UUID.randomUUID().toString())
        app.database.archive().saveRemoteAccount(RemoteAccount().apply { owner=user.ownerKey;payload=Gson().toJson(user) })
        app.preferences.finishOnboarding();app.preferences.session(user.ownerKey);app.preferences.language("pt")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var ready=false
                var startup=""
                val deadline=android.os.SystemClock.elapsedRealtime()+20000
                while(!ready && android.os.SystemClock.elapsedRealtime()<deadline) {
                    Thread.sleep(200)
                    scenario.onActivity {
                        val route=ViewModelProvider(it)[MainViewModel::class.java].state.value.route.screen
                        startup="screen=$route, windowFocus=${it.hasWindowFocus()}, needsSignIn=${it.community.state.value.requiresSignIn}"
                        ready=it.hasWindowFocus() && route=="home" && it.community.state.value.requiresSignIn
                    }
                }
                assertTrue("A missing session must be recognized without making authenticated requests: $startup",ready)
                scenario.onActivity { ViewModelProvider(it)[MainViewModel::class.java].navigate(Route("community")) }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val button=activity.window.decorView.findViewWithTag<android.widget.TextView>("community:signin")
                    assertNotNull(button);assertEquals("Entrar novamente",button.text.toString())
                    val vm=ViewModelProvider(activity)[MainViewModel::class.java]
                    vm.drafts["auth:password"]="Must be cleared";vm.drafts["auth:local"]="true"
                    assertTrue(button.performClick())
                    assertEquals("login",vm.state.value.route.screen)
                    assertEquals(user.ownerKey,vm.state.value.user!!.ownerKey)
                    assertEquals(user.email,vm.drafts["auth:email"])
                    assertNull(vm.drafts["auth:password"]);assertNull(vm.drafts["auth:local"])
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val email=activity.findViewById<EditText>(0x01000000 or ("auth:email".hashCode() and 0x00FFFFFF))
                    assertEquals(user.email,email.text.toString())
                }
                assertNotNull(app.database.archive().remoteAccount(user.ownerKey))
            }
        } finally { app.preferences.session("guest") }
    }

    @Test fun directComposerSurvivesStateRefreshAndRemainsAboveKeyboard()=runBlocking<Unit> {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as MarvelApplication
        val user=UserProfile("UI test","ui@example.invalid",username="ui_hero",id=UUID.randomUUID().toString())
        val conversation=UUID.randomUUID().toString()
        app.database.archive().saveRemoteAccount(RemoteAccount().apply { owner=user.ownerKey;payload=Gson().toJson(user) })
        app.preferences.finishOnboarding();app.preferences.session(user.ownerKey);app.preferences.language("pt")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var ready=false
            var startup=""
            val deadline=android.os.SystemClock.elapsedRealtime()+20000
            while(!ready && android.os.SystemClock.elapsedRealtime()<deadline) {
                Thread.sleep(200)
                scenario.onActivity {
                    val route=ViewModelProvider(it)[MainViewModel::class.java].state.value.route.screen
                    startup="screen=$route, windowFocus=${it.hasWindowFocus()}"
                    ready=it.hasWindowFocus() && route=="home"
                }
            }
            assertTrue("Android must finish opening the test window: $startup",ready)
            scenario.onActivity { ViewModelProvider(it)[MainViewModel::class.java].navigate(Route("directChat",userId=conversation)) }
            instrumentation.waitForIdleSync();Thread.sleep(500)
            lateinit var original: EditText
            scenario.onActivity { activity ->
                original=activity.findViewById(0x01000000 or ("direct:$conversation".hashCode() and 0x00FFFFFF))
                val send=activity.window.decorView.findViewWithTag<android.view.ViewGroup>("direct:send")
                assertNotNull(send)
                val icon=send.getChildAt(0) as android.widget.ImageView
                assertNotNull("The send icon must be a bundled drawable, even while offline",icon.drawable)
                assertEquals(send.width/2f,icon.left+icon.width/2f,1f)
                assertEquals(send.height/2f,icon.top+icon.height/2f,1f)
                original.requestFocus();original.setText("Olá comunidade!")
                activity.getSystemService(InputMethodManager::class.java).showSoftInput(original,InputMethodManager.SHOW_IMPLICIT)
            }
            var keyboard=false
            val imeDeadline=android.os.SystemClock.elapsedRealtime()+15000
            while(!keyboard && android.os.SystemClock.elapsedRealtime()<imeDeadline) {
                Thread.sleep(200)
                scenario.onActivity { keyboard=androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true }
            }
            assertTrue("Keyboard must open",keyboard)
            scenario.onActivity { it.refresh() }
            instrumentation.waitForIdleSync();Thread.sleep(400)
            scenario.onActivity { activity ->
                val current=activity.findViewById<EditText>(original.id)
                assertSame("A state refresh must retain the input connection",original,current)
                assertEquals("Olá comunidade!",current.text.toString())
                assertTrue(current.hasFocus())
                val visible=Rect();assertTrue(current.getGlobalVisibleRect(visible))
                assertTrue(visible.height()>=current.height-2)
                val insets=androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)!!
                val top=activity.windowManager.currentWindowMetrics.bounds.bottom-insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
                assertTrue("Composer must remain above the keyboard",visible.bottom<=top)
                assertNull(activity.window.decorView.findViewWithTag<View>("navigation:home"))
            }
        }
        app.preferences.session("guest")
    }
}
