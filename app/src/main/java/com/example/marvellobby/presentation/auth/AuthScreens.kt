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
     add(authHero(ui,register),0)
     add(ui.text(if(register)"Discover stories. Find your people." else "Your favorites, stories and people, together.",12,ui.palette.muted),12)
     val form=ui.column(16).apply {
         background=ui.gradient(ui.palette.surface,ColorUtils.blendARGB(ui.palette.surface,ui.palette.raised,0.35f),26).apply { setStroke(ui.dp(1),ColorUtils.setAlphaComponent(ui.palette.border,160)) }
         tag="auth:form"
     }
     ui.add(form,ui.text(if(register)"Create account" else "Sign in",20,bold=true),0)
     val fields=linkedMapOf<String,EditText>()
     val frames=linkedMapOf<String,LinearLayout>()
     val errors=linkedMapOf<String,TextView>()
     val checks=mutableListOf<Pair<(String)->Boolean,TextView>>()
     fun validation()=AuthValidation.validate(register,true,vm.drafts["auth:name"].orEmpty(),vm.drafts["auth:email"].orEmpty(),vm.drafts["auth:password"].orEmpty(),vm.drafts["auth:confirm"].orEmpty(),vm.drafts["auth:username"].orEmpty())
     fun updateHints() {
         val current=validation()
         fields.forEach { (key,input) ->
             val message=vm.state.value.authErrors[key] ?: current[key].takeIf { key in vm.authTouched }
             errors.getValue(key).apply {
                 text=message?.let(ui.translate).orEmpty()
                 visibility=if(message==null)View.GONE else View.VISIBLE
             }
             frames.getValue(key).background=ui.shape(ColorUtils.blendARGB(ui.palette.background,ui.palette.surface,0.45f),14,true).apply {
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
         caption.addView(ui.text(label,12,ui.palette.muted,true),LinearLayout.LayoutParams(0,-2,1f))
         if(key=="name")caption.addView(ui.text("2–80 characters",10,ui.palette.muted),LinearLayout.LayoutParams(-2,-2))
         ui.add(box,caption,0)
         val input=ui.field(label,vm.drafts["auth:$key"].orEmpty(),hint,password,email,"auth:$key").apply {
             isEnabled=!state.authBusy
             background=null;minimumHeight=ui.dp(52);setPadding(0,ui.dp(10),ui.dp(4),ui.dp(10))
             imeOptions=if(key=="confirm" || (!register && key=="password"))EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
             if(key=="username")inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
             if(key=="name")inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
             if(password) {
                 transformationMethod=if("auth:$key" in state.visibleSecrets)null else PasswordTransformationMethod.getInstance()
                 if(register)setAutofillHints("newPassword")
             }
         }
         val row=ui.row().apply { setPadding(ui.dp(14),0,ui.dp(4),0);tag="auth:$key:container" }
         val prefix=if(key=="username")ui.text("@",18,ui.palette.muted,true).apply { gravity=Gravity.CENTER;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
             else ImageView(activity).apply {
                 setImageResource(when { password->R.drawable.ic_auth_lock;email->R.drawable.ic_auth_mail;else->R.drawable.ic_auth_user })
                 imageTintList=ColorStateList.valueOf(ui.palette.muted);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
             }
         row.addView(prefix,LinearLayout.LayoutParams(ui.dp(20),ui.dp(20)).apply { marginEnd=ui.dp(12) })
         row.addView(input,LinearLayout.LayoutParams(0,ui.dp(52),1f))
         if(password)row.addView(ui.actionIcon(if("auth:$key" in state.visibleSecrets)R.drawable.ic_eye else R.drawable.ic_eye_closed,if("auth:$key" in state.visibleSecrets)"Hide password" else "Show password") {
             vm.toggleAuthPassword("auth:$key")
         }.apply { isEnabled=!state.authBusy;tag="auth:$key:visibility";background=ui.shape(Color.TRANSPARENT,12) },LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)))
         ui.add(box,row,6)
         val error=ui.text("",12,ui.palette.red).apply { tag="auth:$key:error";visibility=View.GONE;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
         ui.add(box,error,6)
         fields[key]=input;frames[key]=row;errors[key]=error
         input.doAfterTextChanged { vm.authFieldEdited(key,it.toString());updateHints() }
         input.onFocusChangeListener=View.OnFocusChangeListener { _,hasFocus -> if(!hasFocus)vm.authTouched.add(key);updateHints() }
         ui.add(form,box,14)
         return input
     }
     if(register) {
         field("Name","name")
         field("Username","username",hint="your_username")
         ui.add(form,ui.text(AuthValidation.USERNAME_HINT,11,ui.palette.muted),6)
     }
     field("Email","email",email=true)
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
     state.formError?.let { ui.add(form,ui.text(it,12,ui.palette.red).apply { tag="auth:summary";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE },12) }
     if(state.authBusy)ui.add(form,ui.loading(if(register)"Creating account…" else "Signing in…"),12)
     if(!vm.onlineAvailable)ui.add(form,ui.text("The online service is not configured yet.",12,ui.palette.red),12)
     fun submit() {
         activity.hideKeyboard()
         vm.authenticate(register,vm.drafts["auth:name"].orEmpty(),vm.drafts["auth:email"].orEmpty(),vm.drafts["auth:password"].orEmpty(),vm.drafts["auth:confirm"].orEmpty(),vm.drafts["auth:username"].orEmpty())
     }
     fields.getValue(if(register)"confirm" else "password").setOnEditorActionListener { _,action,_ -> if(action==EditorInfo.IME_ACTION_DONE) { submit();true } else false }
     ui.add(form,ui.button(if(register)"Create account" else "Sign in") { submit() }.apply { isEnabled=!state.authBusy && vm.onlineAvailable;tag="auth:submit";alpha=if(isEnabled)1f else 0.55f },16,52)
     add(form,20)
     val prompt=ui.translate(if(register)"Already have an account?" else "New to Marvel Lobby?")
     val action=ui.translate(if(register)"Sign in" else "Create account")
     add(ui.text("",12).apply {
         text=SpannableString("$prompt  $action").apply {
             setSpan(ForegroundColorSpan(ui.palette.secondary),length-action.length,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
             setSpan(StyleSpan(android.graphics.Typeface.BOLD),length-action.length,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
         }
         gravity=Gravity.CENTER;minimumHeight=ui.dp(48);isEnabled=!state.authBusy;isClickable=true;isFocusable=true;tag="auth:switch"
         setOnClickListener { vm.navigate(Route(if(register)"login" else "register"),replaceCurrent=true) }
     },8)
     add(ui.text("Continue as guest",12,ui.palette.muted).apply {
         gravity=Gravity.CENTER;minimumHeight=ui.dp(44);isEnabled=!state.authBusy;isClickable=true;isFocusable=true;setOnClickListener { vm.guest() }
     },0)
 }

