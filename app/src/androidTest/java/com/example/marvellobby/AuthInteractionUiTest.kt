package com.example.marvellobby

import android.graphics.Rect
import android.os.SystemClock
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.marvellobby.presentation.MainViewModel
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator-only fixture. Real touch/key events exercise the input connection and scroll viewport. */
@RunWith(AndroidJUnit4::class)
class AuthInteractionUiTest {
    private fun all(view:View):List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList())
    private fun field(activity:MainActivity,key:String)=activity.findViewById<EditText>(0x01000000 or ("auth:$key".hashCode() and 0x00FFFFFF))
    private fun tagged(activity:MainActivity,tag:String)=all(activity.window.decorView).first { it.tag==tag }
    private fun waitFor(scenario:ActivityScenario<MainActivity>,condition:(MainActivity)->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+20000
        var ready=false
        while(!ready && SystemClock.elapsedRealtime()<deadline) { Thread.sleep(100);scenario.onActivity { ready=condition(it) } }
        assertTrue("Login must remain usable with the keyboard open",ready)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
    private fun keyboard(activity:MainActivity)=ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true
    private fun visibleAboveKeyboard(activity:MainActivity,input:EditText):Boolean {
        val rect=Rect();val viewport=Rect()
        return input.getGlobalVisibleRect(rect) && tagged(activity,"screen:scroll").getGlobalVisibleRect(viewport) &&
            rect.height()==input.height && viewport.contains(rect)
    }

    @Test fun touchTypingValidationVisibilityAndScrollingKeepTheSameInputs()=runBlocking<Unit> {
        assertTrue("Use a dedicated emulator, never a user's phone",android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic"))
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MarvelApplication
        app.preferences.language("pt");app.preferences.session("");app.preferences.appearance("dark")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor(scenario) { it.hasWindowFocus() && field(it,"email")!=null }
            lateinit var originalEmail:EditText
            lateinit var originalPassword:EditText
            scenario.onActivity { originalEmail=field(it,"email");originalPassword=field(it,"password") }
            onView(withTagValue(equalTo("auth:submit"))).perform(scrollTo(),click())
            waitFor(scenario) { tagged(it,"auth:email:error").visibility==View.VISIBLE }
            // Tap the leading icon area, not the EditText: the whole outlined field must respond.
            onView(withTagValue(equalTo("auth:email:container"))).perform(scrollTo(),GeneralClickAction(Tap.SINGLE,{ view ->
                val location=IntArray(2);view.getLocationOnScreen(location)
                floatArrayOf(location[0]+24*view.resources.displayMetrics.density,location[1]+view.height/2f)
            },Press.FINGER))
            waitFor(scenario) { field(it,"email").hasFocus() && keyboard(it) && visibleAboveKeyboard(it,field(it,"email")) }
            onView(withId(originalEmail.id)).perform(typeText("breno@example.invalid"))
            scenario.onActivity { activity ->
                assertSame("Correcting errors must not recreate the input",originalEmail,field(activity,"email"))
                assertEquals("breno@example.invalid",originalEmail.text.toString())
                assertTrue(keyboard(activity));assertTrue(originalEmail.hasFocus())
                assertEquals(View.GONE,tagged(activity,"auth:email:error").visibility)
            }
            onView(withId(originalEmail.id)).perform(pressImeActionButton())
            waitFor(scenario) { originalPassword.hasFocus() && visibleAboveKeyboard(it,originalPassword) }
            onView(withId(originalPassword.id)).perform(typeText("test1"))
            onView(withTagValue(equalTo("auth:password:visibility"))).perform(click())
            scenario.onActivity { activity ->
                assertSame(originalPassword,field(activity,"password"))
                assertNull(originalPassword.transformationMethod)
                assertEquals("test1",originalPassword.text.toString())
                assertTrue(originalPassword.hasFocus());assertTrue(keyboard(activity))
            }
            onView(withId(originalPassword.id)).perform(typeText("234"))
            scenario.onActivity { activity ->
                assertEquals("test1234",originalPassword.text.toString())
                // Unrelated community/application emissions must also preserve auth inputs and caret.
                activity.refresh()
                assertSame(originalEmail,field(activity,"email"));assertSame(originalPassword,field(activity,"password"))
                assertEquals(originalPassword.length(),originalPassword.selectionStart)
                ViewModelProvider(activity)[MainViewModel::class.java].hideSensitive()
            }
            waitFor(scenario) { originalPassword.transformationMethod is PasswordTransformationMethod }
            // Swipe while the IME is still open, then interact with the footer through actual touches.
            onView(withTagValue(equalTo("screen:scroll"))).perform(swipeUp())
            scenario.onActivity { assertTrue((tagged(it,"screen:scroll") as ScrollView).scrollY>0) }
            var previousY=-1
            var settled=0
            waitFor(scenario) {
                val y=(tagged(it,"screen:scroll") as ScrollView).scrollY
                settled=if(y==previousY)settled+1 else 0
                previousY=y
                settled>=5 // A tap during a fling stops it; wait for normal scrolling to finish.
            }
            onView(withTagValue(equalTo("auth:switch"))).perform(scrollTo(),click())
            scenario.onActivity { assertEquals("A touch on Create account must open signup","register",ViewModelProvider(it)[MainViewModel::class.java].state.value.route.screen) }
            waitFor(scenario) { field(it,"username")!=null }
            onView(withTagValue(equalTo("auth:switch"))).perform(scrollTo(),click())
            waitFor(scenario) { ViewModelProvider(it)[MainViewModel::class.java].state.value.route.screen=="login" && field(it,"email")!=null }
            scenario.onActivity { activity ->
                assertEquals("breno@example.invalid",field(activity,"email").text.toString())
                assertTrue(field(activity,"password").transformationMethod is PasswordTransformationMethod)
                // Change the theme only after the keyboard interaction checks.
                ViewModelProvider(activity)[MainViewModel::class.java].appearance("light")
            }
            waitFor(scenario) { ViewModelProvider(it)[MainViewModel::class.java].state.value.preferences.appearance=="light" }
            onView(withTagValue(equalTo("auth:email:container"))).perform(scrollTo(),click())
            waitFor(scenario) { field(it,"email").hasFocus() && keyboard(it) && visibleAboveKeyboard(it,field(it,"email")) }
            onView(withId(originalEmail.id)).perform(typeText("x"))
            scenario.onActivity { assertTrue(field(it,"email").text.toString().contains("x")) }
            onView(isRoot()).perform(closeSoftKeyboard())
        }
    }
}
