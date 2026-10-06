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
        gravity=Gravity.TOP;filters=arrayOf(InputFilter { source,start,end,dest,dstart,dend ->
            val retained=dest.subSequence(0,dstart).toString()+dest.subSequence(dend,dest.length).toString()
            val incoming=source.subSequence(start,end).toString()
            val available=(280-retained.codePointCount(0,retained.length)).coerceAtLeast(0)
            if(incoming.codePointCount(0,incoming.length)<=available)null else incoming.substring(0,incoming.offsetByCodePoints(0,available))
        })
        doAfterTextChanged { vm.drafts[key]=it.toString() }
    }
    ui.add(box,field,8,112);add(box)
    val counter=ui.text("",12,ui.palette.muted).apply { gravity=Gravity.END }
    fun count() { val value=field.text.toString();counter.text="${value.codePointCount(0,value.length)} / 280" }
    count();field.doAfterTextChanged { count() };ui.add(box,counter,8)
    body(if(state.user?.online==false)"Up to 280 characters. Saved on this device." else "Up to 280 characters. Visible on your public profile.")
}

fun ScreenRenderer.editBio() {
    val user=state.user ?: return
    if(user.email=="guest")return
    title("Tell your story")
    bioField("bio:text",user.bio)
    state.formError?.let(::body)
    if(state.authBusy)add(ui.loading("Saving…"))
    add(ui.button("Save bio") {
        activity.hideKeyboard();vm.saveBio(vm.drafts["bio:text"] ?: user.bio)
    }.apply { isEnabled=!state.authBusy },height=52)
}

fun ScreenRenderer.connectAccount() {
    val local=state.user ?: return
    if(local.online || local.email=="guest" || !vm.onlineAvailable)return
    val create=vm.drafts["connect:mode"]!="link"
    label("ONLINE ACCOUNT")
    title(if(create)"Create your online identity" else "Connect an existing account")
    body("Your favorites, history and AI conversations stay on this device and will be associated with the online account you choose. Favorites can be shared later from your profile. Confirm both accounts before continuing.")
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
    user.id?.let { publicFavorites(it) }
    if(state.user?.online==true)user.id?.let { socialStats(it) }
    label("Member since")
    add(ui.text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(user.joinedAt)),14),gap=8)
}
