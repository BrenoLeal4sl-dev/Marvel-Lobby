package com.example.marvellobby

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import androidx.lifecycle.*
import com.example.marvellobby.presentation.*
import com.example.marvellobby.presentation.social.*
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var vm: MainViewModel
    lateinit var community: CommunityViewModel
        private set
    private var retainedDirectRoot: LinearLayout?=null
    private var directStyle=""
    private var directFirst=0L
    private var directLast=0L
    private var directCount=0
    private var scroll: ScrollView?=null
    private var renderedRoute=""
    private var lastState: AppState?=null
    private var navigationBar: BottomNavigationBar?=null
    private var navigationStyle=""
    private var lastChatId: String?=null
    private var lastChatMessageCount=0
    private var credentialSignal: android.os.CancellationSignal?=null
    private var retainedChatRoot: LinearLayout?=null
    private var retainedChatStyle=""
    private var avatarDialog: androidx.appcompat.app.AlertDialog?=null
    private lateinit var screenHost: ScreenTransitionHost
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        screenHost=ScreenTransitionHost(this).apply { tag="screen:host" }
        setContentView(screenHost)
        vm=ViewModelProvider(this)[MainViewModel::class.java]
        community=ViewModelProvider(this)[CommunityViewModel::class.java]
        onBackPressedDispatcher.addCallback(this,object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goBack() }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect { state -> lastState=state;render(state) } }
                launch { community.state.collect { lastState?.let(::render) } }
                launch { for(destination in community.navigation)vm.navigate(destination) }
                launch { for(message in vm.messages)Toast.makeText(this@MainActivity,Translations.text(message,vm.state.value.preferences.language),Toast.LENGTH_SHORT).show() }
            }
        }
    }
    fun refresh() { lastState?.let(::render) }
    fun pickAvatar() {
        val prefs=vm.state.value.preferences
        val light=prefs.appearance=="light" || (prefs.appearance=="system" && resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_NO)
        avatarDialog?.dismiss()
        avatarDialog=showAvatarChooser(this,vm,UiKit(this,Palette(light)) { Translations.text(it,prefs.language) })
        avatarDialog?.setOnDismissListener { avatarDialog=null }
    }
    fun authorizeSensitive(action: ()->Unit) {
        val guard=getSystemService(android.app.KeyguardManager::class.java)
        if(!guard.isDeviceSecure) { action();return }
        val key=vm.state.value.route.key
        val account=vm.state.value.user?.email
        credentialSignal?.cancel()
        val signal=android.os.CancellationSignal().also { credentialSignal=it }
        android.hardware.biometrics.BiometricPrompt.Builder(this)
            .setTitle(Translations.text("Confirm device credentials",vm.state.value.preferences.language))
            .setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build().authenticate(signal,mainExecutor,object: android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult) {
                    if(vm.state.value.route.key==key && vm.state.value.user?.email==account)action()
                    credentialSignal=null
                }
                override fun onAuthenticationError(errorCode: Int,errString: CharSequence) { credentialSignal=null }
            })
    }
    fun hideKeyboard() { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(currentFocus?.windowToken,0);currentFocus?.clearFocus() }
    private fun goBack() {
        if(ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true) {
            hideKeyboard()
        } else if(!vm.back()) finish()
    }
    fun openLink(url: String) { runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }.onFailure { Toast.makeText(this,"No browser available",Toast.LENGTH_SHORT).show() } }

    private fun render(state: AppState) {
        community.route(state.route)
        val style="${state.preferences.language}:${state.preferences.appearance}"
        if(state.route.screen=="directChat" && renderedRoute==state.route.key && retainedDirectRoot!=null && directStyle==style) { updateDirectChat(state);return }
        if(state.route.screen!="directChat")retainedDirectRoot=null
        val chatStyle=state.preferences.language
        if(state.route.screen=="ai" && renderedRoute==state.route.key && retainedChatRoot!=null && retainedChatStyle==chatStyle) {
            updateChat(state)
            return
        }
        if(state.route.screen!="ai")retainedChatRoot=null
        val oldFocus=currentFocus as? EditText
        val focusId=oldFocus?.id
        val selection=oldFocus?.selectionStart ?: 0
        val sameRoute=renderedRoute==state.route.key
        if(renderedRoute.isNotBlank())scroll?.let { vm.scrollPositions[renderedRoute]=it.scrollY }
        if(!sameRoute)hideKeyboard()
        val aiTheme=state.route.screen in listOf("ai","chats")
        val isLight=!aiTheme && (state.preferences.appearance=="light" || (state.preferences.appearance=="system" && resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_NO))
        val palette=Palette(isLight,aiTheme)
        val ui=UiKit(this,palette) { Translations.text(it,state.preferences.language) }
        val root=ui.column().apply {
            layoutParams=android.view.ViewGroup.LayoutParams(-1,-1)
            if(aiTheme)background=ui.gradient(palette.background,android.graphics.Color.parseColor("#260C16"),0) else setBackgroundColor(palette.background)
            isFocusableInTouchMode=true
        }
        val insetsController=WindowCompat.getInsetsController(window,window.decorView)
        insetsController.isAppearanceLightStatusBars=isLight
        insetsController.isAppearanceLightNavigationBars=isLight
        ViewCompat.setOnApplyWindowInsetsListener(root) { v,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime=insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left,bars.top,bars.right,maxOf(bars.bottom,ime.bottom))
            if(state.route.screen !in listOf("ai","directChat"))navigationBar?.visibility=if(ime.bottom>0)View.GONE else View.VISIBLE
            insets
        }
        if(state.route.screen=="splash") {
            root.addView(SplashView(ui),LinearLayout.LayoutParams(-1,0,1f))
            scroll=null
        } else {
            root.addView(header(state,ui),LinearLayout.LayoutParams(-1,ui.dp(68)))
            val renderer=ScreenRenderer(this,vm,state,ui)
            scroll=ScrollView(this).apply {
                isFillViewport=true;isVerticalScrollBarEnabled=false
                addView(renderer.render())
            }
            val stage=FrameLayout(this)
            stage.addView(scroll,FrameLayout.LayoutParams(-1,-1))
            root.addView(stage,LinearLayout.LayoutParams(-1,0,1f))
            if(state.route.screen=="ai") {
                root.addView(renderer.aiComposer(),LinearLayout.LayoutParams(-1,-2))
            }
            if(state.route.screen=="directChat") {
                root.addView(renderer.directComposer(),LinearLayout.LayoutParams(-1,-2))
                scroll?.setOnScrollChangeListener { _: View, _: Int, _: Int, _: Int, _: Int -> readDirectWhenVisible(state) }
            }
            if(state.route.screen !in listOf("welcome","login","register","filters","ai","directChat")) {
                val style="$isLight:${state.preferences.language}"
                if(navigationBar==null || style!=navigationStyle) {
                    navigationBar?.stopAnimations()
                    navigationBar=BottomNavigationBar(ui) { vm.tab(it) }
                    navigationStyle=style
                }
                val bar=navigationBar!!
                bar.visibility=View.VISIBLE
                (bar.parent as? android.view.ViewGroup)?.removeView(bar)
                scroll?.apply { setPadding(0,0,0,ui.dp(104));clipToPadding=false }
                stage.addView(bar,FrameLayout.LayoutParams(-1,ui.dp(80),Gravity.BOTTOM).apply {
                    leftMargin=ui.dp(16);rightMargin=ui.dp(16);bottomMargin=ui.dp(12)
                })
            }
        }
        val previousScreen=renderedRoute.substringBefore(':')
        val animateAi=!sameRoute && previousScreen !in listOf("","splash") && (previousScreen=="ai" || state.route.screen=="ai")
        screenHost.show(root,sameRoute,animateAi,state.route.screen=="ai")
        if(state.route.screen=="ai") { retainedChatRoot=root;retainedChatStyle=chatStyle }
        if(state.route.screen=="directChat") {
            retainedDirectRoot=root;directStyle=style
            val messages=community.state.value.chats[state.route.userId]?.messages.orEmpty()
            directFirst=messages.firstOrNull()?.id ?: 0;directLast=messages.lastOrNull()?.id ?: 0
            directCount=messages.size
        }
        navigationBar?.select(state.route.section ?: "home")
        renderedRoute=state.route.key
        ViewCompat.requestApplyInsets(root)
        val scrollChat=state.route.screen=="ai" && (lastChatId!=state.ai.conversationId || lastChatMessageCount!=state.ai.messages.size || state.ai.sending)
        if(state.route.screen=="ai") { lastChatId=state.ai.conversationId;lastChatMessageCount=state.ai.messages.size }
        val activeScroll=scroll
        activeScroll?.post {
            if(scroll===activeScroll && renderedRoute==state.route.key) {
                if(state.route.screen=="directChat") { activeScroll.scrollTo(0,activeScroll.getChildAt(0).height);readDirectWhenVisible(state) }
                else if(state.route.screen=="ai" && state.ai.messages.isEmpty())activeScroll.scrollTo(0,0)
                else if(scrollChat)activeScroll.scrollTo(0,activeScroll.getChildAt(0).height)
                else activeScroll.scrollTo(0,vm.scrollPositions[state.route.key] ?: 0)
            }
        }
        if(sameRoute && focusId!=null)root.findViewById<EditText>(focusId)?.let { edit ->
            edit.requestFocus();edit.setSelection(selection.coerceAtMost(edit.text.length))
        }
    }
    /** Incoming state replaces only the transcript, preserving the EditText and its IME connection. */
    private fun updateChat(state: AppState) {
        val palette=Palette(false,true)
        val ui=UiKit(this,palette) { Translations.text(it,state.preferences.language) }
        val transcript=scroll ?: return
        val shouldScroll=lastChatId!=state.ai.conversationId || lastChatMessageCount!=state.ai.messages.size || state.ai.sending
        val oldY=transcript.scrollY
        transcript.removeAllViews()
        transcript.addView(ScreenRenderer(this,vm,state,ui).render())
        val root=retainedChatRoot ?: return
        val field=root.findViewById<EditText>(0x01000000 or ("chat".hashCode() and 0x00FFFFFF))
        val draft=vm.drafts["chat"].orEmpty()
        if(field.text.toString()!=draft) { field.setText(draft);field.setSelection(draft.length) }
        root.findViewWithTag<View>("chat:send")?.apply { isEnabled=!state.ai.sending;alpha=if(state.ai.sending)0.45f else 1f }
        lastChatId=state.ai.conversationId;lastChatMessageCount=state.ai.messages.size
        transcript.post {
            if(scroll===transcript && vm.state.value.route.screen=="ai") {
                if(state.ai.messages.isEmpty())transcript.scrollTo(0,0)
                else if(shouldScroll)transcript.scrollTo(0,transcript.getChildAt(0).height)
                else transcript.scrollTo(0,oldY)
            }
        }
    }
    private fun readDirectWhenVisible(state: AppState) {
        val view=scroll ?: return
        if(state.route.screen=="directChat" && view.childCount>0 && view.scrollY+view.height>=view.getChildAt(0).height-24)
            state.route.userId?.let { community.markRead(it) }
    }
    private fun updateDirectChat(state: AppState) {
        val root=retainedDirectRoot ?: return
        val view=scroll ?: return
        val id=state.route.userId ?: return
        val chat=community.state.value.chats[id]
        val messages=chat?.messages.orEmpty()
        val first=messages.firstOrNull()?.id ?: 0L
        val last=messages.lastOrNull()?.id ?: 0L
        val oldY=view.scrollY
        val oldHeight=if(view.childCount>0)view.getChildAt(0).height else 0
        val wasBottom=oldY+view.height>=oldHeight-48
        val older=directFirst>0 && first<directFirst
        val newMessage=last!=directLast || messages.size>directCount
        val light=state.preferences.appearance=="light" || (state.preferences.appearance=="system" && resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_NO)
        val ui=UiKit(this,Palette(light)) { Translations.text(it,state.preferences.language) }
        view.removeAllViews();view.addView(ScreenRenderer(this,vm,state,ui).render())
        val field=root.findViewById<EditText>(0x01000000 or ("direct:$id".hashCode() and 0x00FFFFFF))
        val draft=community.drafts[id].orEmpty()
        if(field.text.toString()!=draft) { field.setText(draft);field.setSelection(draft.length) }
        root.findViewWithTag<View>("direct:send")?.apply {
            isEnabled=!community.state.value.requiresSignIn && chat?.loaded==true && chat.pending==null && !chat.sending
            alpha=if(isEnabled)1f else 0.4f
        }
        directFirst=first;directLast=last;directCount=messages.size
        view.post {
            if(scroll===view && vm.state.value.route.key==state.route.key) {
                val height=view.getChildAt(0).height
                if(older)view.scrollTo(0,oldY+(height-oldHeight))
                else if(newMessage && (wasBottom || messages.lastOrNull()?.senderId==community.userId))view.scrollTo(0,height)
                else view.scrollTo(0,oldY)
                readDirectWhenVisible(state)
            }
        }
    }
    private fun header(state: AppState,ui: UiKit): View {
        val row=ui.row().apply { setPadding(ui.dp(20),ui.dp(10),ui.dp(20),ui.dp(10)) }
        if(state.route.screen=="home") {
            val brand=FrameLayout(this)
            brand.addView(ui.brandLogo(48).apply { scaleType=ImageView.ScaleType.FIT_START;translationX=-ui.dp(12).toFloat() },FrameLayout.LayoutParams(ui.dp(120),-1,Gravity.START or Gravity.CENTER_VERTICAL))
            row.addView(brand,LinearLayout.LayoutParams(0,-1,1f))
            row.addView(ui.avatar(state.user?.avatar.orEmpty(),state.user?.name ?: "Profile",44).apply {
                contentDescription=ui.translate("Profile");isFocusable=true;setOnClickListener { vm.navigate(Route("profile")) }
            })
        } else {
            row.addView(ui.icon("back","Back") { goBack() })
            val title=when(state.route.screen) {
                "login","register"->"Account";"editProfile"->"Edit Profile";"editBio"->"Biography";"privacy"->"Privacy & terms"
                "connectAccount"->"Connect online account";"publicProfile"->"Public profile"
                "community"->"Community";"inbox"->"Messages";"directChat"->"Messages"
                "socialPeople"->if(state.route.title=="following")"Following" else "Followers"
                "history"->"Recently Viewed";"chats"->"Conversation history";"ai"->"Marvel AI";"catalog"->when(state.route.type?.name) {
                    "CHARACTER"->"Characters";"TEAM"->"Teams";"POWER"->"Powers";else->"Story Arcs"
                }
                "detail"->when(state.route.type?.name) {
                    "CHARACTER"->"Character dossier";"TEAM"->"Team record";"POWER"->"Power record";"STORY_ARC"->"Story record";else->"Issue record"
                }
                else->state.route.screen.replaceFirstChar { it.uppercase() }
            }
            row.addView(ui.text(title,18,bold=true).apply { setPadding(ui.dp(12),0,0,0) },LinearLayout.LayoutParams(0,-2,1f))
            if(state.route.screen=="ai") {
                row.addView(ui.actionIcon(R.drawable.ic_chat_history,"Conversation history") { vm.navigate(Route("chats")) }.apply { tag="chat:history" })
                row.addView(ui.actionIcon(R.drawable.ic_new_chat,"New conversation",ui.palette.raised) { vm.ask(null) }.apply {
                    tag="chat:new"
                },LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)).apply { marginStart=ui.dp(6) })
            }
        }
        return row
    }
    override fun onStart() {
        super.onStart()
        community.foreground(true)
    }
    override fun onStop() {
        community.foreground(false)
        super.onStop()
        vm.hideSensitive()
    }
    override fun onDestroy() {
        avatarDialog?.dismiss()
        screenHost.dispose()
        retainedChatRoot=null
        retainedDirectRoot=null
        credentialSignal?.cancel()
        scroll?.let { vm.scrollPositions[renderedRoute]=it.scrollY }
        navigationBar?.stopAnimations()
        super.onDestroy()
    }
}

