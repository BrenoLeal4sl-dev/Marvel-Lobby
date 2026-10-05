package com.example.marvellobby.presentation.social

import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.example.marvellobby.presentation.*
import java.text.DateFormat
import java.util.Date

fun ScreenRenderer.bioField(key: String,initial: String="") {
    val box=ui.column()
    ui.add(box,ui.text("Biography",12,ui.palette.secondary,true),0)
    val field=ui.field("Biography",vm.drafts[key] ?: initial,idKey=key).apply {
        inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        gravity=Gravity.TOP;filters=arrayOf(InputFilter.LengthFilter(280))
        doAfterTextChanged { vm.drafts[key]=it.toString() }
    }
    ui.add(box,field,8,112);add(box)
    body("Up to 280 characters. Visible on your public profile.")
}

fun ScreenRenderer.connectAccount() {
    val local=state.user ?: return
    if(local.online || local.email=="guest" || !vm.onlineAvailable)return
    val create=vm.drafts["connect:mode"]!="link"
    label("ONLINE ACCOUNT")
    title(if(create)"Create your online identity" else "Connect an existing account")
    body("Your favorites, history and AI conversations stay on this device and will be associated with the online account you choose. They are not uploaded. Confirm both accounts before continuing.")
    button(if(create)"I already have an online account" else "Create an online account",false) {
        vm.drafts["connect:mode"]=if(create)"link" else "create"
        vm.navigate(state.route,replaceCurrent=true)
    }
    formField("Local account password","connect:localPassword",password=true)
    formField("Email","connect:email",email=true,initial=local.email)
    formField("Online account password","connect:password",password=true)
    if(create) {
        formField("Confirm password","connect:confirm",password=true)
        formField("Username","connect:username",initial=local.username).filters=arrayOf(InputFilter.LengthFilter(25))
        body("Use 3–24 letters, numbers or underscores for your username.")
        bioField("connect:bio")
    }
    state.formError?.let(::body)
    if(state.authBusy)add(ui.loading())
    add(ui.button("Connect online account") {
        activity.hideKeyboard()
        vm.connectOnline(create,vm.drafts["connect:email"] ?: local.email,vm.drafts["connect:password"].orEmpty(),
            vm.drafts["connect:confirm"].orEmpty(),vm.drafts["connect:localPassword"].orEmpty(),
            vm.drafts["connect:username"] ?: local.username,vm.drafts["connect:bio"].orEmpty())
    }.apply { isEnabled=!state.authBusy },height=52)
}

fun ScreenRenderer.publicProfile() {
    if(communitySessionRequired())return
    label("PUBLIC PROFILE")
    if(state.publicProfileLoading) { add(ui.loading());return }
    state.publicProfileError?.let { sectionError(it) { vm.loadPublicProfile(state.route.userId.orEmpty()) };return }
    val user=state.publicProfile ?: return
    val row=ui.row().apply { gravity=Gravity.CENTER }
    row.addView(ui.avatar(user.avatar,user.name,100),LinearLayout.LayoutParams(ui.dp(100),ui.dp(100)))
    add(row)
    add(ui.title(user.name).apply { gravity=Gravity.CENTER })
    add(ui.text("@${user.username}",16,ui.palette.secondary).apply { gravity=Gravity.CENTER },gap=8)
    if(user.bio.isNotBlank())add(ui.text(user.bio,14,ui.palette.muted).apply { text=user.bio })
    if(state.user?.online==true)user.id?.let { socialStats(it) }
    label("Member since")
    add(ui.text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(user.joinedAt)),14),gap=8)
}
