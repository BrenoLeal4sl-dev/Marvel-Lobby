package com.example.marvellobby.presentation.auth

import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.example.marvellobby.presentation.ScreenRenderer

fun ScreenRenderer.chooseLanguage() {
    add(ui.text("OLÁ / HELLO",12,ui.palette.secondary,true),0)
    add(ui.text("Escolha seu idioma\nChoose your language",28,bold=true),16)
    add(ui.text("Comece o Marvel Lobby na sua língua.\nStart Marvel Lobby in your language.",14,ui.palette.muted),16)
    listOf(Triple("pt","Português","Aplicativo e mensagens em português"),Triple("en","English","App and messages in English")).forEach { (code,name,description) ->
        add(ui.menu(name,description) { vm.chooseInitialLanguage(code) }.apply {
            tag="language:$code";isEnabled=!state.authBusy
            background=ui.gradient(ui.palette.surface,ui.palette.raised)
        },24)
    }
    add(ui.text("Você pode mudar depois nas configurações.\nYou can change this later in Settings.",12,ui.palette.muted),24)
    state.formError?.let { body(it) }
    if(state.authBusy)add(ui.loading("Salvando / Saving…"))
}

fun ScreenRenderer.languageMenu() {
    val row=ui.row()
    row.addView(ui.text("Idioma / Language",12,ui.palette.muted),LinearLayout.LayoutParams(0,-2,1f))
    row.addView(ui.button(if(state.preferences.language=="pt")"Português" else "English",false) {
        AlertDialog.Builder(activity).setTitle("Idioma / Language")
            .setSingleChoiceItems(arrayOf("Português","English"),if(state.preferences.language=="pt")0 else 1) { dialog,index ->
                vm.language(if(index==0)"pt" else "en");dialog.dismiss()
            }.show()
    }.apply { tag="auth:language";isEnabled=!state.authBusy })
    add(row,16)
}
