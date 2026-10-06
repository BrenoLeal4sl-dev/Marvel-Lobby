package com.example.marvellobby.presentation.auth

import com.example.marvellobby.presentation.*
import com.example.marvellobby.data.model.*
import android.widget.*
import android.view.View
import android.view.Gravity
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.graphics.ColorUtils
import androidx.core.view.isNotEmpty
import android.view.inputmethod.EditorInfo
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import androidx.core.widget.doAfterTextChanged
import com.example.marvellobby.R

/** Updates validation and progress without replacing the focused input or its IME connection. */
class AuthFormBinding(private val refresh:(AppState)->Unit) {
    fun update(state:AppState)=refresh(state)
}

fun ScreenRenderer.welcome() {
     add(ui.editorial("ARCHIVE / 00","A universe of connections",190))
     languageMenu()
     add(ui.text("Explore the\nMarvel Universe",32,bold=true))
     body("Follow the people, powers and stories that connect everything.")
     label("CHARACTERS / TEAMS / POWERS / STORIES / AI")
     button("Get Started") { vm.onboarding(true) }
     button("I already have an account",false) { vm.onboarding(false) }
 }
fun ScreenRenderer.auth() {
     val register=state.route.screen=="register"
     if(register) {
         add(authHero(ui,true),0)
         add(ui.text("Discover stories. Find your people.",12,ui.palette.muted),12)
     } else {
         content.setPadding(ui.dp(24),ui.dp(24),ui.dp(24),ui.dp(24))
         add(ui.text("YOUR MARVEL UNIVERSE",10,ui.palette.secondary,true).apply { letterSpacing=0.12f },0)
         add(ui.text("Welcome back",30,bold=true),12)
         add(ui.text("Sign in to continue your story.",14,ui.palette.muted),12)
     }
     val form=ui.column(if(register)16 else 0).apply {
         if(register)background=ui.gradient(ui.palette.surface,ColorUtils.blendARGB(ui.palette.surface,ui.palette.raised,0.35f),26).apply { setStroke(ui.dp(1),ColorUtils.setAlphaComponent(ui.palette.border,160)) }
         tag="auth:form"
     }
     if(register)ui.add(form,ui.text("Create account",20,bold=true),0)
     val fields=linkedMapOf<String,EditText>()
     val frames=linkedMapOf<String,LinearLayout>()
     val errors=linkedMapOf<String,TextView>()
     val checks=mutableListOf<Pair<(String)->Boolean,TextView>>()
     val eyeButtons=linkedMapOf<String,View>()
     fun validation()=AuthValidation.validate(register,true,vm.drafts["auth:name"].orEmpty(),vm.drafts["auth:email"].orEmpty(),vm.drafts["auth:password"].orEmpty(),vm.drafts["auth:confirm"].orEmpty(),vm.drafts["auth:username"].orEmpty())
     fun updateHints() {
         val current=validation()
         fields.forEach { (key,input) ->
             val message=vm.state.value.authErrors[key] ?: current[key].takeIf { key in vm.authTouched }
             errors.getValue(key).apply {
                 val value=message?.let(ui.translate).orEmpty()
                 if(text.toString()!=value)text=value
                 val shown=if(message==null)View.GONE else View.VISIBLE
                 if(visibility!=shown)visibility=shown
             }
             frames.getValue(key).background=ui.shape(ui.palette.surface,if(register)14 else 18,true).apply {
                 setStroke(ui.dp(1),when { message!=null->ui.palette.red;input.hasFocus()->ui.palette.secondary;else->ui.palette.border })
             }
             input.contentDescription=ui.translate(when(key) { "name"->"Name";"username"->"Username";"email"->"Email";"confirm"->"Confirm password";else->"Password" })+message?.let { ": "+ui.translate(it) }.orEmpty()
         }
         val password=vm.drafts["auth:password"].orEmpty()
         checks.forEach { (rule,text) ->
             val met=rule(password)
             val short=when(text.tag) { "8–128 characters"->"8–128 chars";"At least one letter"->"One letter";else->"One number" }
             text.text=(if(met)"✓ " else "○ ")+ui.translate(short)
             text.setTextColor(if(met)ui.palette.secondary else ui.palette.muted)
             text.contentDescription=ui.translate(text.tag as String)+", "+ui.translate(if(met)"Requirement met" else "Requirement pending")
         }
     }
     fun field(label:String,key:String,password:Boolean=false,email:Boolean=false,hint:String=""):EditText {
         val box=ui.column()
         val caption=ui.row()
         caption.addView(ui.text(label,if(register)12 else 13,ui.palette.muted,true),LinearLayout.LayoutParams(0,-2,1f))
         if(key=="name")caption.addView(ui.text("2–80 characters",10,ui.palette.muted),LinearLayout.LayoutParams(-2,-2))
         ui.add(box,caption,0)
         val input=ui.field(label,vm.drafts["auth:$key"].orEmpty(),hint,password,email,"auth:$key").apply {
             isEnabled=!state.authBusy
             textSize=if(register)14f else 16f
             background=null;minimumHeight=ui.dp(56);setPadding(0,ui.dp(10),ui.dp(4),ui.dp(10))
             imeOptions=if(key=="confirm" || (!register && key=="password"))EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
             if(key=="username")inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
             if(key=="name")inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
             if(password) {
                 transformationMethod=if("auth:$key" in state.visibleSecrets)null else PasswordTransformationMethod.getInstance()
                 if(register)setAutofillHints("newPassword")
             }
         }
         val row=ui.row().apply { setPadding(ui.dp(14),0,ui.dp(4),0);tag="auth:$key:container" }
         row.setOnClickListener {
             if(input.isEnabled) {
                 input.requestFocus()
                 (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
             }
         }
         row.isFocusable=false
         val prefix=if(key=="username")ui.text("@",18,ui.palette.muted,true).apply { gravity=Gravity.CENTER;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
             else ImageView(activity).apply {
                 setImageResource(when { password->R.drawable.ic_auth_lock;email->R.drawable.ic_auth_mail;else->R.drawable.ic_auth_user })
                 imageTintList=ColorStateList.valueOf(ui.palette.muted);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
             }
         row.addView(prefix,LinearLayout.LayoutParams(ui.dp(20),ui.dp(20)).apply { marginEnd=ui.dp(12) })
         row.addView(input,LinearLayout.LayoutParams(0,ui.dp(56),1f))
         if(password)row.addView(ui.actionIcon(if("auth:$key" in state.visibleSecrets)R.drawable.ic_eye else R.drawable.ic_eye_closed,if("auth:$key" in state.visibleSecrets)"Hide password" else "Show password") {
             vm.toggleAuthPassword("auth:$key")
         }.apply { isEnabled=!state.authBusy;tag="auth:$key:visibility";background=ui.shape(Color.TRANSPARENT,12);eyeButtons[key]=this },LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
         ui.add(box,row,8)
         val error=ui.text("",12,ui.palette.red).apply { tag="auth:$key:error";visibility=View.GONE;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
         ui.add(box,error,6)
         fields[key]=input;frames[key]=row;errors[key]=error
         input.doAfterTextChanged { vm.authFieldEdited(key,it.toString());updateHints() }
         input.onFocusChangeListener=View.OnFocusChangeListener { _,hasFocus -> if(!hasFocus)vm.authTouched.add(key);updateHints() }
         ui.add(form,box,if(register)14 else 20)
         return input
     }
     if(register) {
         field("Name","name")
         field("Username","username",hint="your_username")
         ui.add(form,ui.text(AuthValidation.USERNAME_HINT,11,ui.palette.muted),6)
     }
     field("Email","email",email=true,hint="you@example.com")
     field("Password","password",password=true)
     if(register) {
         val requirements=ui.column()
         val strip=ui.row()
         listOf("8–128 characters" to PasswordRules::length,"At least one letter" to PasswordRules::letter,"At least one number" to PasswordRules::number).forEach { (label,rule) ->
             val text=ui.text(label,10,ui.palette.muted).apply { tag=label;setPadding(ui.dp(4),ui.dp(8),ui.dp(4),ui.dp(8));gravity=Gravity.CENTER;background=ui.shape(ui.palette.raised,10) }
             strip.addView(text,LinearLayout.LayoutParams(0,-2,1f).apply { if(strip.isNotEmpty())marginStart=ui.dp(6) });checks.add(rule to text)
         }
         ui.add(requirements,strip,0)
         ui.add(requirements,ui.text("Uppercase letters and symbols are optional.",11,ui.palette.muted),8)
         ui.add(form,requirements,8)
         field("Confirm password","confirm",password=true)
     }
     updateHints()
     val summary=ui.text("",12,ui.palette.red).apply { tag="auth:summary";visibility=View.GONE;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
     ui.add(form,summary,12)
     if(!vm.onlineAvailable)ui.add(form,ui.text("The online service is not configured yet.",12,ui.palette.red),12)
     fun submit() {
         vm.authenticate(register,vm.drafts["auth:name"].orEmpty(),vm.drafts["auth:email"].orEmpty(),vm.drafts["auth:password"].orEmpty(),vm.drafts["auth:confirm"].orEmpty(),vm.drafts["auth:username"].orEmpty())
         if(vm.state.value.authErrors.isNotEmpty())fields[vm.state.value.authErrors.keys.first()]?.let { input ->
             input.requestFocus()
             input.post { input.requestRectangleOnScreen(android.graphics.Rect(0,0,input.width,input.height+ui.dp(20)),false) }
         } else activity.hideKeyboard()
     }
     fields.values.toList().forEachIndexed { index,input ->
         input.setOnEditorActionListener { _,action,_ ->
             when(action) {
                 EditorInfo.IME_ACTION_NEXT -> {
                     fields.values.elementAtOrNull(index+1)?.let { next ->
                         next.requestFocus()
                         (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(next,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                     }
                     true
                 }
                 EditorInfo.IME_ACTION_DONE -> { submit();true }
                 else -> false
             }
         }
     }
     val submitButton=ui.button(if(register)"Create account" else "Sign in") { submit() }.apply { tag="auth:submit" }
     ui.add(form,submitButton,if(register)16 else 28,56)
     add(form,if(register)20 else 28)
     val prompt=ui.translate(if(register)"Already have an account?" else "New to Marvel Lobby?")
     val action=ui.translate(if(register)"Sign in" else "Create account")
     val switch=ui.text("",14).apply {
         text=SpannableString("$prompt  $action").apply {
             setSpan(ForegroundColorSpan(ui.palette.secondary),length-action.length,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
             setSpan(StyleSpan(android.graphics.Typeface.BOLD),length-action.length,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
         }
         gravity=Gravity.CENTER;minimumHeight=ui.dp(48);isEnabled=!state.authBusy;isClickable=true;isFocusable=true;tag="auth:switch"
         setOnClickListener { vm.navigate(Route(if(register)"login" else "register"),replaceCurrent=true) }
     }
     add(switch,12)
     val guest=ui.text("Continue as guest",13,ui.palette.muted).apply {
         gravity=Gravity.CENTER;minimumHeight=ui.dp(44);isEnabled=!state.authBusy;isClickable=true;isFocusable=true;setOnClickListener { vm.guest() }
     }
     add(guest,0)
     authForm=AuthFormBinding { current ->
         updateHints()
         fields.forEach { (key,input) ->
             input.isEnabled=!current.authBusy
             if(key in eyeButtons) {
                 val visible="auth:$key" in current.visibleSecrets
                 val desired=if(visible)null else PasswordTransformationMethod.getInstance()
                 if(input.transformationMethod!==desired) {
                     val start=input.selectionStart;val end=input.selectionEnd
                     input.transformationMethod=desired
                     if(start>=0 && end>=0)input.setSelection(start.coerceAtMost(input.length()),end.coerceAtMost(input.length()))
                 }
                 eyeButtons.getValue(key).apply {
                     isEnabled=!current.authBusy
                     contentDescription=ui.translate(if(visible)"Hide password" else "Show password")
                     ((this as android.view.ViewGroup).getChildAt(0) as ImageView).setImageResource(if(visible)R.drawable.ic_eye else R.drawable.ic_eye_closed)
                 }
             }
         }
         val message=current.formError?.takeUnless { it in current.authErrors.values }
         summary.text=message?.let(ui.translate).orEmpty();summary.visibility=if(message==null)View.GONE else View.VISIBLE
         submitButton.apply {
             text=ui.translate(if(current.authBusy) { if(register)"Creating account…" else "Signing in…" } else if(register)"Create account" else "Sign in")
             contentDescription=text;isEnabled=!current.authBusy && vm.onlineAvailable;alpha=if(isEnabled)1f else 0.55f
         }
         switch.isEnabled=!current.authBusy;guest.isEnabled=!current.authBusy
         activity.window.decorView.findViewWithTag<View>("auth:language")?.isEnabled=!current.authBusy
     }.also { it.update(state) }
 }

