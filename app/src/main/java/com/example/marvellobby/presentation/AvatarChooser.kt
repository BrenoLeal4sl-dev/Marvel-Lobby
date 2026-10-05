package com.example.marvellobby.presentation

import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.example.marvellobby.R

fun showAvatarChooser(activity: com.example.marvellobby.MainActivity,vm: MainViewModel,ui: UiKit): AlertDialog? {
    val choices=vm.avatarChoices
    if(choices.isEmpty())return null
    var selected=choices.firstOrNull { it.uri==vm.state.value.user?.avatar } ?: choices.first()
    val body=ui.column(24)
    ui.add(body,ui.title("Choose your hero"),0)
    val preview=FrameLayout(activity)
    val name=ui.text(selected.name,18,bold=true).apply { gravity=Gravity.CENTER }
    fun updatePreview() {
        preview.removeAllViews()
        preview.addView(ui.avatar(selected.uri,selected.name,128),FrameLayout.LayoutParams(ui.dp(128),ui.dp(128),Gravity.CENTER))
        name.text=selected.name
    }
    updatePreview()
    ui.add(body,preview,20,height=136);ui.add(body,name,12)
    val strip=ui.row()
    val optionViews=mutableListOf<Pair<String,android.view.View>>()
    choices.forEach { avatar ->
        val option=ui.column(6).apply {
            gravity=Gravity.CENTER;contentDescription=avatar.name;isFocusable=true
        }
        option.addView(ui.avatar(avatar.uri,avatar.name,72))
        ui.add(option,ui.text(avatar.name,11).apply { gravity=Gravity.CENTER;maxLines=2 },8,width=82)
        optionViews.add(avatar.uri to option)
        option.background=ui.shape(if(avatar.uri==selected.uri)ui.palette.raised else ui.palette.surface,22)
        option.setOnClickListener {
            selected=avatar;updatePreview()
            optionViews.forEach { (uri,view) -> view.background=ui.shape(if(uri==selected.uri)ui.palette.raised else ui.palette.surface,22,uri==selected.uri) }
        }
        strip.addView(option,LinearLayout.LayoutParams(ui.dp(94),-2).apply { marginEnd=ui.dp(8) })
    }
    ui.add(body,HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled=false;addView(strip) },20)
    val dialog=AlertDialog.Builder(activity).setView(body).create()
    ui.add(body,ui.button("Use avatar") { vm.chooseAvatar(selected.uri);dialog.dismiss() },24)
    ui.add(body,ui.button("Cancel",false) { dialog.dismiss() },10)
    dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(ui.shape(ui.palette.surface,28)) }
    dialog.show()
    return dialog
}

fun ScreenRenderer.profileAvatar(editable: Boolean=true) {
    val user=state.user ?: return
    val frame=FrameLayout(activity).apply { clipChildren=false }
    frame.addView(ui.avatar(user.avatar,user.name,112),FrameLayout.LayoutParams(ui.dp(112),ui.dp(112),Gravity.CENTER))
    if(editable && user.email!="guest")frame.addView(ui.actionIcon(R.drawable.ic_pencil,"Change avatar",ui.palette.red) { activity.pickAvatar() }.apply {
        translationX=ui.dp(42).toFloat();translationY=ui.dp(42).toFloat()
    },FrameLayout.LayoutParams(ui.dp(40),ui.dp(40),Gravity.CENTER))
    add(frame,height=144)
}
