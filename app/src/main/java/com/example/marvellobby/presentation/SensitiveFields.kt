package com.example.marvellobby.presentation

import android.text.method.PasswordTransformationMethod
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.example.marvellobby.R

fun ScreenRenderer.secureField(label: String,key: String,initial: String="",email: Boolean=false) {
    val box=ui.column()
    ui.add(box,ui.label(label),0)
    val line=ui.row()
    val field=ui.field(label,vm.drafts[key] ?: initial,password=!email,email=email,idKey=key).apply {
        if(key !in state.visibleSecrets)transformationMethod=PasswordTransformationMethod.getInstance()
        else transformationMethod=null
        isEnabled=state.securityUnlocked && !state.authBusy
        doAfterTextChanged { vm.drafts[key]=it.toString() }
    }
    line.addView(field,LinearLayout.LayoutParams(0,ui.dp(56),1f))
    line.addView(ui.actionIcon(if(key in state.visibleSecrets)R.drawable.ic_eye else R.drawable.ic_eye_closed,"Show or hide field") {
        if(key in state.visibleSecrets)vm.hideSecret(key) else activity.authorizeSensitive { vm.revealSecret(key) }
    },LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)).apply { marginStart=ui.dp(8) })
    ui.add(box,line,8);add(box)
}
