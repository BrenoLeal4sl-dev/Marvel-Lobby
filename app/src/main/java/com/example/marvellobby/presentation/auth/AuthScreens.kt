package com.example.marvellobby.presentation.auth

import com.example.marvellobby.presentation.*
import com.example.marvellobby.data.model.*
import android.widget.*

fun ScreenRenderer.welcome() {
     add(ui.editorial("ARCHIVE / 00","A universe of connections",190))
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
     if(vm.onlineAvailable) {
         body(if(online)"Online account · Marvel Lobby" else "Local account · stored only on this device")
         button(if(online)"Use a local account" else "Use an online account",false) {
             vm.drafts["auth:local"]=online.toString()
             vm.navigate(state.route,replaceCurrent=true)
         }
     }
     if(register)formField("Name","auth:name")
     if(register && online) {
         formField("Username","auth:username").filters=arrayOf(android.text.InputFilter.LengthFilter(25))
         body("Use 3–24 letters, numbers or underscores for your username.")
     }
     formField("Email","auth:email",email=true)
     formField("Password","auth:password",password=true)
     if(register)formField("Confirm password","auth:confirm",password=true)
     state.formError?.let { add(ui.text(it,14,ui.palette.red)) }
     if(state.authBusy)add(ui.loading())
     add(ui.button(if(register)"Create account" else "Sign in") {
         activity.hideKeyboard()
         vm.authenticate(register,vm.drafts["auth:name"].orEmpty(),vm.drafts["auth:email"].orEmpty(),vm.drafts["auth:password"].orEmpty(),vm.drafts["auth:confirm"].orEmpty(),online,vm.drafts["auth:username"].orEmpty())
     }.apply { isEnabled=!state.authBusy },height=52)
     button(if(register)"I already have an account" else "Create account",false) { vm.navigate(Route(if(register)"login" else "register"),replaceCurrent=true) }
     menu("Continue as guest") { vm.guest() }
     if(!vm.onlineAvailable)body("Local account · stored only on this device")
 }

