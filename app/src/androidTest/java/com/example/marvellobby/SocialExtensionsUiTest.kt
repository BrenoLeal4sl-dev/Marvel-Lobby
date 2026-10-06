package com.example.marvellobby

import android.graphics.Bitmap
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.marvellobby.data.local.AppPreferences
import com.example.marvellobby.data.model.*
import com.example.marvellobby.data.repository.UserProfile
import com.example.marvellobby.presentation.*
import com.example.marvellobby.presentation.social.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Render production screens with synthetic state; no accounts or messages are created online. */
@RunWith(AndroidJUnit4::class)
class SocialExtensionsUiTest {
    @Test fun socialAndCloudScreensRenderInPortugueseInBothThemes() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val user=UserProfile("Hero","","","hero","11111111-1111-4111-8111-111111111111","Minha próxima descoberta.")
        val conversation="22222222-2222-4222-8222-222222222222"
        val record=SharedContent(ResourceType.POWER,1,"Flight",null)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for(light in listOf(false,true))for(screen in listOf("community","activity","notifications","socialPrivacy","cloudSync","directChat")) {
                scenario.onActivity { activity ->
                    activity.community.foreground(false)
                    val route=Route(screen,userId=if(screen=="directChat")conversation else null)
                    @Suppress("UNCHECKED_CAST")
                    val mutable=CommunityViewModel::class.java.getDeclaredField("mutable").apply { isAccessible=true }.get(activity.community) as MutableStateFlow<CommunityState>
                    mutable.value=CommunityState(
                        people=mapOf(route.key to PeopleState(items=listOf(user),loaded=true)),connected=true,
                        activity=ActivityState(listOf(CommunityActivity(user,record,1_780_000_000_000L)),loaded=true),
                        activitySharing=FavoriteSharingState(false),
                        notifications=NotificationsState(listOf(CommunityNotification("follow:demo","follow",user,1_780_000_000_000L,false,null)),loaded=true,unread=1),
                        cloud=CloudSyncState(enabled=false),
                        chats=mapOf(conversation to DirectChatState(peer=user,loaded=true,messages=listOf(
                            DirectMessage(1,conversation,user.id!!,"33333333-3333-4333-8333-333333333333","Flight",1_780_000_000_000L,record)))))
                    val ui=UiKit(activity,Palette(light)) { Translations.text(it,"pt") }
                    val state=AppState(route=route,user=user,preferences=AppPreferences(language="pt",appearance=if(light)"light" else "dark"))
                    val renderer=ScreenRenderer(activity,ViewModelProvider(activity)[MainViewModel::class.java],state,ui)
                    val root=ui.column().apply { setBackgroundColor(ui.palette.background);setPadding(0,ui.dp(32),0,ui.dp(16)) }
                    val content=renderer.render();assertTrue(content.childCount>0)
                    val scroll=ScrollView(activity).apply { addView(content) }
                    root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
                    if(screen=="directChat")root.addView(renderer.directComposer())
                    activity.setContentView(root,android.view.ViewGroup.LayoutParams(-1,-1))
                }
                instrumentation.waitForIdleSync()
                // Accessibility may attach after the activity has finished laying out.
                val deadline=android.os.SystemClock.uptimeMillis()+10_000
                while(instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()!=context.packageName &&
                    android.os.SystemClock.uptimeMillis()<deadline) {
                    android.os.SystemClock.sleep(100)
                }
                assertEquals("An Android system dialog is covering the app",context.packageName,
                    instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
                val screenshot=instrumentation.uiAutomation.takeScreenshot()
                assertNotNull(screenshot)
                val output=File(context.filesDir,"qa/social-$screen-${if(light)"light" else "dark"}.png").apply { parentFile!!.mkdirs() }
                output.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) };screenshot.recycle()
            }
        }
    }
}
