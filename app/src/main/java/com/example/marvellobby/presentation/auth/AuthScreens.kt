package com.example.marvellobby.presentation.auth

import com.example.marvellobby.presentation.*
import com.example.marvellobby.data.model.*
import android.widget.*
import android.view.View
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
     val online=vm.onlineAvailable && vm.drafts["auth:local"]!="true"
     label("ME / ACCESS");title(if(register)"Your archive starts here" else "Welcome back")
     body("Keep your discoveries together.")
     languageMenu()
     if(vm.onlineAvailable) {
         body(if(online)"Online account · Marvel Lobby" else "Local account · stored only on this device")
         add(ui.button(if(online)"Use a local account" else "Use an online account",false) {
             vm.drafts["auth:local"]=online.toString()
             vm.navigate(state.route,replaceCurrent=true)
         }.apply { isEnabled=!state.authBusy },height=52)
     }
     val fields=linkedMapOf<String,EditText>()
     val errors=linkedMapOf<String,TextView>()
     val checks=mutableListOf<Pair<(String)->Boolean,TextView>>()
     fun validation()=AuthValidation.validate(register,online,vm.drafts["auth:name"].orEmpty(),vm.drafts["auth:email"].orEmpty(),vm.drafts["auth:password"].orEmpty(),vm.drafts["auth:confirm"].orEmpty(),vm.drafts["auth:username"].orEmpty())
     fun updateHints() {
         val current=validation()
         fields.forEach { (key,input) ->
             val message=vm.state.value.authErrors[key] ?: current[key].takeIf { key in vm.authTouched }
             errors.getValue(key).apply {
                 text=message?.let(ui.translate).orEmpty()
                 visibility=if(message==null)View.GONE else View.VISIBLE
             }
             input.background=ui.shape(ui.palette.surface,border=true).apply { setStroke(ui.dp(1),if(message==null)ui.palette.border else ui.palette.red) }
             input.contentDescription=ui.translate(when(key) { "name"->"Name";"username"->"Username";"email"->"Email";"confirm"->"Confirm password";else->"Password" })+message?.let { ": "+ui.translate(it) }.orEmpty()
         }
         val password=vm.drafts["auth:password"].orEmpty()
         checks.forEach { (rule,text) ->
             val met=rule(password)
             text.text=(if(met)"✓  " else "○  ")+ui.translate(text.tag as String)
             text.setTextColor(if(met)ui.palette.secondary else ui.palette.muted)
             text.contentDescription=ui.translate(text.tag as String)+", "+ui.translate(if(met)"Requirement met" else "Requirement pending")
         }
     }
     fun field(label:String,key:String,password:Boolean=false,email:Boolean=false,hint:String=""):EditText {
         val box=ui.column()
         ui.add(box,ui.text(label,12,ui.palette.secondary,true),0)
         val input=ui.field(label,vm.drafts["auth:$key"].orEmpty(),hint,password,email,"auth:$key").apply {
             isEnabled=!state.authBusy
             imeOptions=if(key=="confirm" || (!register && key=="password"))EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
             if(key=="username")inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
             if(key=="name")inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
             if(password) {
                 transformationMethod=if("auth:$key" in state.visibleSecrets)null else PasswordTransformationMethod.getInstance()
                 if(register)setAutofillHints("newPassword")
             }
         }
         val row=ui.row()
         row.addView(input,LinearLayout.LayoutParams(0,ui.dp(56),1f))
         if(password)row.addView(ui.actionIcon(if("auth:$key" in state.visibleSecrets)R.drawable.ic_eye else R.drawable.ic_eye_closed,if("auth:$key" in state.visibleSecrets)"Hide password" else "Show password") {
             vm.toggleAuthPassword("auth:$key")
         }.apply { isEnabled=!state.authBusy;tag="auth:$key:visibility" },LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)).apply { marginStart=ui.dp(8) })
         ui.add(box,row,8)
         val error=ui.text("",12,ui.palette.red).apply { tag="auth:$key:error";visibility=View.GONE;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
         ui.add(box,error,6)
         fields[key]=input;errors[key]=error
         input.doAfterTextChanged { vm.authFieldEdited(key,it.toString());updateHints() }
         input.onFocusChangeListener=View.OnFocusChangeListener { _,hasFocus -> if(!hasFocus) { vm.authTouched.add(key);updateHints() } }
         add(box)
         return input
     }
     if(register) { field("Name","name");add(ui.text("2–80 characters",12,ui.palette.muted),6) }
     if(register && online) {
         field("Username","username",hint="brenoleal1234")
         add(ui.text(AuthValidation.USERNAME_HINT,12,ui.palette.muted),6)
     }
     field("Email","email",email=true)
     field("Password","password",password=true)
     if(register) {
         val requirements=ui.column(14).apply { background=ui.shape(ui.palette.surface,16,true) }
         listOf("8–128 characters" to PasswordRules::length,"At least one letter" to PasswordRules::letter,"At least one number" to PasswordRules::number).forEach { (label,rule) ->
             val text=ui.text(label,12,ui.palette.muted).apply { tag=label }
             ui.add(requirements,text,8);checks.add(rule to text)
         }
         ui.add(requirements,ui.text("Uppercase letters and symbols are optional.",12,ui.palette.muted),12)
         add(requirements,10)
         field("Confirm password","confirm",password=true)
     }
     updateHints()
     state.formError?.let { add(ui.text(it,14,ui.palette.red).apply { tag="auth:summary";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }) }
     if(state.authBusy)add(ui.loading(if(register)"Creating account…" else "Signing in…"))
     fun submit() {
         activity.hideKeyboard()
         vm.authenticate(register,vm.drafts["auth:name"].orEmpty(),vm.drafts["auth:email"].orEmpty(),vm.drafts["auth:password"].orEmpty(),vm.drafts["auth:confirm"].orEmpty(),online,vm.drafts["auth:username"].orEmpty())
     }
     fields.getValue(if(register)"confirm" else "password").setOnEditorActionListener { _,action,_ -> if(action==EditorInfo.IME_ACTION_DONE) { submit();true } else false }
     add(ui.button(if(register)"Create account" else "Sign in") { submit() }.apply { isEnabled=!state.authBusy;tag="auth:submit" },height=52)
     add(ui.button(if(register)"I already have an account" else "Create account",false) { vm.navigate(Route(if(register)"login" else "register"),replaceCurrent=true) }.apply { isEnabled=!state.authBusy },height=52)
     menu("Continue as guest") { vm.guest() }
     if(!vm.onlineAvailable)body("Local account · stored only on this device")
 }

