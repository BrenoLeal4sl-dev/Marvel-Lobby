package com.example.marvellobby

import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.marvellobby.data.remote.LobbyProtocol
import com.example.marvellobby.presentation.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthOnboardingUiTest {
    private fun all(view:View):List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList())
    private fun input(activity:MainActivity,key:String)=activity.findViewById<EditText>(0x01000000 or ("auth:$key".hashCode() and 0x00FFFFFF))
    private fun tagged(activity:MainActivity,tag:String)=all(activity.window.decorView).first { it.tag==tag }
    private fun waitFor(scenario:ActivityScenario<MainActivity>,condition:(MainActivity)->Boolean) {
        val deadline=android.os.SystemClock.elapsedRealtime()+20000
        var ready=false
        while(!ready && android.os.SystemClock.elapsedRealtime()<deadline) { Thread.sleep(100);scenario.onActivity { ready=condition(it) } }
        assertTrue("Expected auth UI must become available",ready)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    /** Run on a fresh emulator installation: never clear a real user's preferences to exercise onboarding. */
    @Test fun firstLaunchChoosesLanguageAndSignupExplainsRulesAndSpecificErrors()=runBlocking<Unit> {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MarvelApplication
        assertFalse("This onboarding check requires a fresh test installation",app.preferences.flow.first().languageChosen)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor(scenario) { it.hasWindowFocus() && ViewModelProvider(it)[MainViewModel::class.java].state.value.route.screen=="language" }
            scenario.onActivity { activity ->
                val labels=all(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue(labels.any { it.contains("Escolha seu idioma") && it.contains("Choose your language") })
                assertTrue(tagged(activity,"language:pt").performClick())
            }
            waitFor(scenario) { ViewModelProvider(it)[MainViewModel::class.java].state.value.route.screen=="welcome" }
            assertTrue(app.preferences.flow.first().languageChosen)
            assertFalse("Language selection alone must not complete onboarding",app.preferences.flow.first().onboarded)
            scenario.onActivity {
                val vm=ViewModelProvider(it)[MainViewModel::class.java]
                vm.drafts["auth:local"]="true" // An old mode selection must never enable local signup again.
                vm.onboarding(true)
            }
            waitFor(scenario) { input(it,"confirm")!=null }
            scenario.onActivity { activity ->
                assertNotNull(input(activity,"username"))
                val hints=all(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
                assertFalse(hints.any { it.contains("conta local") || it.contains("local account") })
                assertTrue(hints.any { it.contains("Sem pontos, espaços ou acentos") })
                assertTrue(hints.any { it.contains("Letras maiúsculas e símbolos são opcionais") })
                input(activity,"name").setText("Breno")
                input(activity,"username").setText("breno.leal")
                input(activity,"email").setText("breno@example.invalid")
                input(activity,"password").setText("abc")
                input(activity,"confirm").setText("other")
                assertTrue(tagged(activity,"auth:submit").performClick())
            }
            waitFor(scenario) { tagged(it,"auth:username:error").visibility==View.VISIBLE }
            scenario.onActivity { activity ->
                val state=ViewModelProvider(activity)[MainViewModel::class.java].state.value
                assertFalse(state.authBusy);assertEquals("register",state.route.screen)
                assertEquals(setOf("username","password","confirm"),state.authErrors.keys)
                assertTrue((tagged(activity,"auth:username:error") as TextView).text.contains("Sem pontos"))
                assertEquals("breno.leal",input(activity,"username").text.toString())
                input(activity,"username").setText("breno_leal")
                input(activity,"password").setText("password1")
                input(activity,"confirm").setText("password1")
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(View.GONE,tagged(activity,"auth:username:error").visibility)
                assertEquals(View.GONE,tagged(activity,"auth:password:error").visibility)
                assertEquals(View.GONE,tagged(activity,"auth:confirm:error").visibility)
                assertEquals(3,all(activity.window.decorView).filterIsInstance<TextView>().count { it.text.startsWith("✓") })
                assertTrue(input(activity,"password").transformationMethod is PasswordTransformationMethod)
                assertTrue(tagged(activity,"auth:password:visibility").performClick())
            }
            waitFor(scenario) { input(it,"password").transformationMethod==null }
            scenario.onActivity { activity ->
                assertEquals("password1",input(activity,"password").text.toString())
                val vm=ViewModelProvider(activity)[MainViewModel::class.java]
                assertFalse(vm.state.value.securityUnlocked)
                vm.hideSensitive()
            }
            waitFor(scenario) { input(it,"password").transformationMethod is PasswordTransformationMethod }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor(scenario) { ViewModelProvider(it)[MainViewModel::class.java].state.value.route.screen=="login" }
            scenario.onActivity { activity ->
                assertEquals("pt",ViewModelProvider(activity)[MainViewModel::class.java].state.value.preferences.language)
                assertFalse(all(activity.window.decorView).filterIsInstance<TextView>().any { it.text.contains("conta local") })
                assertTrue(tagged(activity,"auth:submit").performClick())
            }
            waitFor(scenario) { tagged(it,"auth:email:error").visibility==View.VISIBLE }
            scenario.onActivity { activity ->
                assertEquals("Digite seu e-mail.",(tagged(activity,"auth:email:error") as TextView).text.toString())
                assertEquals("Digite sua senha.",(tagged(activity,"auth:password:error") as TextView).text.toString())
            }
        }
    }
    @Test fun apiErrorsIdentifyKnownFieldsWithoutDisplayingArbitraryServerText() {
        val username=LobbyProtocol.failure(400,"""{"error":{"code":"INVALID_INPUT","field":"username","message":"private connection data"}}""")
        assertEquals("username",username.field);assertTrue(username.message!!.contains("No dots"));assertFalse(username.message!!.contains("private"))
        val unknown=LobbyProtocol.failure(400,"""{"error":{"code":"INVALID_INPUT","field":"untrusted-field","message":"private"}}""")
        assertNull(unknown.field);assertEquals("Check your input.",unknown.message)
        assertEquals("Email or password is incorrect.",LobbyProtocol.failure(401,"""{"error":{"code":"INVALID_CREDENTIALS","field":"email"}}""").message)
        assertNull(LobbyProtocol.failure(503,"<html>private proxy details</html>").field)
    }
}
