package com.example.marvellobby.presentation

import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.example.marvellobby.data.model.ResourceType

fun ScreenRenderer.ai() {
    val ai=state.ai
    label("MARVEL AI")
    if(ai.messages.isEmpty())title("What do you want\nto know about Marvel?")
    if(ai.context!=null) {
        menu(ui.translate("Context")+" / "+ai.context.name) { vm.open(ai.context) }

    }
    if(ai.messages.isEmpty()) {
        body(if(ai.context!=null)"Ask about this record, its powers, relationships or story." else "Follow a question. Discover a connection.")
        if(ai.context==null) {
            menu("Who is Wolverine?") { vm.suggestQuestion(ResourceType.CHARACTER,"Wolverine",ui.translate("Who is Wolverine?")) }
            menu("What powers does he have?") { vm.suggestQuestion(ResourceType.CHARACTER,"Wolverine",ui.translate("What powers does he have?")) }
            menu("Tell me about the Avengers.") { vm.suggestQuestion(ResourceType.TEAM,"Avengers",ui.translate("Tell me about the Avengers.")) }
            menu("Which characters can regenerate?") { vm.suggestQuestion(ResourceType.POWER,"Healing",ui.translate("Which characters can regenerate?")) }
        }
    }
    ai.messages.forEach { message ->
        val bubble=ui.column(20).apply {
            background=if(message.role=="user")ui.gradient(ui.palette.raised,ui.palette.surface,24) else ui.shape(ui.palette.surface,24,true)
        }
        ui.add(bubble,ui.label(if(message.role=="user")"YOU" else "MARVEL AI"),0)
        ui.add(bubble,ui.text(message.text).apply { setTextIsSelectable(true) },12)
        add(bubble)
    }
    if(ai.sending)add(ThinkingDotsView(ui),height=48)
    ai.error?.let { err ->
        body(err)
        button("Try again",false) { vm.sendChat(ai.messages.lastOrNull { it.role=="user" }?.text.orEmpty(),true) }
    }
    if(ai.sources.isNotEmpty() && ai.messages.any { it.role=="model" }) {
        label("Sources / Comic Vine")
        ai.sources.forEach { source -> menu(source.name) { vm.open(source) } }
    }
    add(ui.text("AI can make mistakes. Check the linked records.",12,ui.palette.muted))
}

fun ScreenRenderer.aiComposer(): android.view.View {
    val line=ui.row().apply {
        setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(12))
        background=ui.gradient(ui.palette.background,ui.palette.surface,0)
    }
    val field=ui.field("Ask a question…",vm.drafts["chat"].orEmpty(),idKey="chat").apply {
        isSingleLine=false;minLines=1;maxLines=4
        inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEND
        filters=arrayOf(android.text.InputFilter.LengthFilter(6000))
        doAfterTextChanged { vm.drafts["chat"]=it.toString() }
    }
    fun send() { if(field.text.isNotBlank())vm.sendChat(field.text.toString()) }
    field.setOnEditorActionListener { _,action,_ ->
        if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { send();true } else false
    }
    line.addView(field,LinearLayout.LayoutParams(0,-2,1f))
    line.addView(ui.actionIcon(com.example.marvellobby.R.drawable.ic_send,"Send question",ui.palette.red) { send() }.apply {
        tag="chat:send"
        isEnabled=!state.ai.sending;alpha=if(state.ai.sending)0.45f else 1f
    },LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)).apply { marginStart=ui.dp(10) })
    return line
}
