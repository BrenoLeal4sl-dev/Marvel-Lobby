package com.example.marvellobby

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.marvellobby.presentation.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatAndProfileUiTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrumentation.targetContext.applicationContext as MarvelApplication
    private fun screenshot(name: String) {
        val bitmap=instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir=java.io.File(app.filesDir,"ui-test-captures").apply { mkdirs() }
        java.io.File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun findDescription(view: View,description: String): View? {
        if(view.contentDescription?.toString()==description)return view
        if(view is ViewGroup)for(index in 0 until view.childCount)findDescription(view.getChildAt(index),description)?.let { return it }
        return null
    }
    private fun awaitReady(scenario: ActivityScenario<MainActivity>) {
        var ready=false
        val deadline=android.os.SystemClock.elapsedRealtime()+20000
        while(!ready && android.os.SystemClock.elapsedRealtime()<deadline) {
            Thread.sleep(200)
            scenario.onActivity {
                ready=it.hasWindowFocus() && ViewModelProvider(it)[MainViewModel::class.java].state.value.route.screen=="home"
            }
        }
        assertTrue("Android must give the app window focus before interaction",ready)
    }
    @Test fun chatComposerRemainsVisibleAboveKeyboardWithNoFloatingNavbar() = runBlocking<Unit> {
        app.preferences.finishOnboarding();app.preferences.session("guest");app.preferences.language("pt");app.preferences.appearance("light")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitReady(scenario)
            scenario.onActivity { ViewModelProvider(it)[MainViewModel::class.java].ask(null) }
            instrumentation.waitForIdleSync();Thread.sleep(600)
            scenario.onActivity { activity ->
                val field=activity.findViewById<EditText>(0x01000000 or ("chat".hashCode() and 0x00FFFFFF))
                assertNotNull(field)
                field.requestFocus();field.setText("Mensagem de teste")
                activity.getSystemService(InputMethodManager::class.java).showSoftInput(field,InputMethodManager.SHOW_IMPLICIT)
            }
            var keyboardVisible=false
            val deadline=android.os.SystemClock.elapsedRealtime()+15000
            while(!keyboardVisible && android.os.SystemClock.elapsedRealtime()<deadline) {
                Thread.sleep(200)
                scenario.onActivity { activity ->
                    keyboardVisible=androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true
                    if(!keyboardVisible)androidx.core.view.WindowCompat.getInsetsController(activity.window,activity.window.decorView).show(androidx.core.view.WindowInsetsCompat.Type.ime())
                }
            }
            scenario.onActivity { ViewModelProvider(it)[MainViewModel::class.java].loadChatHistory() }
            Thread.sleep(1500)
            instrumentation.waitForIdleSync()
            screenshot("ai-keyboard")
            scenario.onActivity { activity ->
                val field=activity.findViewById<EditText>(0x01000000 or ("chat".hashCode() and 0x00FFFFFF))
                val visible=Rect();assertTrue(field.getGlobalVisibleRect(visible))
                assertTrue("The whole input must remain visible",visible.height()>=field.height-2)
                assertNull(activity.window.decorView.findViewWithTag<View>("navigation:ai"))
                val insets=androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)!!
                assertTrue("Keyboard must remain open after state updates",insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()))
                val keyboardTop=activity.windowManager.currentWindowMetrics.bounds.bottom-insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
                assertTrue("Input must be above the keyboard",visible.bottom<=keyboardTop)
            }
        }
    }
    @Test fun profileAvatarAndPickerRenderApprovedOfflineArtwork() = runBlocking<Unit> {
        val email="avatar-${System.nanoTime()}@test.local"
        app.preferences.finishOnboarding()
        app.accounts.register("Teste",email,"Password2026","Password2026")
        app.accounts.update(email,"Teste",app.avatars.choices.first().uri)
        app.preferences.language("pt");app.preferences.appearance("light")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitReady(scenario)
            screenshot("home-light")
            scenario.onActivity { ViewModelProvider(it)[MainViewModel::class.java].navigate(Route("profile")) }
            instrumentation.waitForIdleSync();Thread.sleep(800)
            screenshot("profile-light")
            scenario.onActivity { activity ->
                val handle=activity.window.decorView.findViewWithTag<android.widget.TextView>("profile:username")
                assertNotNull(handle)
                assertTrue(handle.text.startsWith("@"))
                assertNull(findDescription(activity.window.decorView,"Show or hide email"))
                assertNull(findDescription(activity.window.decorView,"Mostrar ou ocultar e-mail"))
                val pencil=findDescription(activity.window.decorView,"Alterar avatar")
                assertNotNull("Profile must offer direct avatar editing",pencil)
                val visible=Rect();assertTrue(pencil!!.getGlobalVisibleRect(visible))
                assertTrue("The pencil badge must not be clipped",visible.height()>=pencil.height-2)
                pencil.performClick()
            }
            instrumentation.waitForIdleSync();Thread.sleep(800)
            screenshot("avatar-picker")
            assertEquals(8,app.avatars.choices.size)
            assertTrue(app.avatars.choices.all { app.avatars.approved(it.uri) })
            assertFalse(app.avatars.approved("content://unapproved/photo"))
        }
    }
    @Test fun aiTransitionSurvivesRefreshAndNewConversationIconIsCentered() = runBlocking<Unit> {
        app.preferences.finishOnboarding();app.preferences.session("guest");app.preferences.language("pt")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitReady(scenario)
            scenario.onActivity { activity ->
                val vm=ViewModelProvider(activity)[MainViewModel::class.java]
                vm.ask(null)
            }
            instrumentation.waitForIdleSync();Thread.sleep(700)
            scenario.onActivity { activity ->
                val icon=activity.window.decorView.findViewWithTag<ViewGroup>("chat:new")
                val history=activity.window.decorView.findViewWithTag<View>("chat:history")
                assertNotNull(icon);assertNotNull(history)
                assertEquals(history.height,icon.height)
                val image=icon.getChildAt(0)
                assertEquals(icon.width/2f,image.left+image.width/2f,1f)
                assertEquals(icon.height/2f,image.top+image.height/2f,1f)
                ViewModelProvider(activity)[MainViewModel::class.java].drafts["chat"]="Discard this draft"
                icon.performClick()
                assertEquals("",ViewModelProvider(activity)[MainViewModel::class.java].drafts["chat"])
                activity.refresh()
            }
            screenshot("ai-header")
            scenario.onActivity { ViewModelProvider(it)[MainViewModel::class.java].tab("home");it.refresh() }
            instrumentation.waitForIdleSync();Thread.sleep(700)
            scenario.onActivity { activity ->
                val host=activity.window.decorView.findViewWithTag<ScreenTransitionHost>("screen:host")
                assertFalse(host.transitioning);assertEquals(1,host.childCount)
                assertNotNull(activity.window.decorView.findViewWithTag<View>("navigation:home"))
            }
        }
    }
}
