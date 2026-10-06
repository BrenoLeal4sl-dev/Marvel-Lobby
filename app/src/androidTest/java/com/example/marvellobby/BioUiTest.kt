package com.example.marvellobby

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.marvellobby.data.local.LocalAccount
import com.example.marvellobby.presentation.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BioUiTest {
    private fun all(view:View):List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList())
    @Test fun bioCanBeEditedDirectlyFromProfileAndAcceptsUnicodeWithoutChangingIdentity()=runBlocking<Unit> {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as MarvelApplication
        val email="bio-ui@example.invalid"
        app.database.archive().insertAccount(LocalAccount().apply { this.email=email;name="Bio hero";username="bio_ui_hero";passwordHash="hash";salt="salt" })
        app.preferences.finishOnboarding();app.preferences.session(email);app.preferences.language("pt")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var ready=false;val deadline=android.os.SystemClock.elapsedRealtime()+20000
                while(!ready && android.os.SystemClock.elapsedRealtime()<deadline) {
                    Thread.sleep(200);scenario.onActivity { ready=it.hasWindowFocus() && ViewModelProvider(it)[MainViewModel::class.java].state.value.route.screen=="home" }
                }
                assertTrue("Test window must finish opening",ready)
                scenario.onActivity { ViewModelProvider(it)[MainViewModel::class.java].navigate(Route("profile")) }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val add=all(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString()=="Adicionar bio" }
                    var action:View=add
                    while(!action.isClickable)action=action.parent as View
                    assertTrue(action.performClick())
                }
                instrumentation.waitForIdleSync()
                val bio="🕷".repeat(280)
                scenario.onActivity { activity ->
                    val input=activity.findViewById<EditText>(0x01000000 or ("bio:text".hashCode() and 0x00FFFFFF))
                    assertNotNull(input);input.setText(bio+"extra");assertEquals(bio,input.text.toString())
                    val save=all(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString()=="Salvar bio" }
                    assertTrue(save.performClick())
                }
                var saved=false;val saveDeadline=android.os.SystemClock.elapsedRealtime()+10000
                while(!saved && android.os.SystemClock.elapsedRealtime()<saveDeadline) {
                    Thread.sleep(100);scenario.onActivity { val state=ViewModelProvider(it)[MainViewModel::class.java].state.value;saved=state.route.screen=="profile" && state.user?.bio==bio }
                }
                assertTrue("Saved bio must return to profile",saved)
                assertEquals(bio,app.database.archive().account(email).bio)
                assertEquals("bio_ui_hero",app.database.archive().account(email).username)
            }
        } finally { app.preferences.session("guest");app.database.archive().deleteAccount(email) }
    }
}
