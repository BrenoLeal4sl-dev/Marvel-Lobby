package com.example.marvellobby.presentation

import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.example.marvellobby.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun ScreenRenderer.chatHistory() {
    title("Conversation history")
    button("New conversation") { vm.ask(null) }
    if(state.chatHistoryLoading)add(ui.loading())
    state.chatHistoryError?.let { sectionError(it) { vm.loadChatHistory() } }
    if(state.chats.isEmpty() && !state.chatHistoryLoading && state.chatHistoryError==null) {
        body("Your conversations will appear here after sending a message.")
    }
    val locale=if(state.preferences.language=="pt")Locale.forLanguageTag("pt-BR") else Locale.ENGLISH
    state.chats.forEach { chat ->
        val line=ui.row().apply { setPadding(ui.dp(16),ui.dp(16),ui.dp(12),ui.dp(16)) }
        val copy=ui.column()
        ui.add(copy,ui.text(chat.title,16,bold=true).apply { maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END },0)
        ui.add(copy,ui.text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,locale).format(Date(chat.updatedAt)),12,ui.palette.muted),8)
        line.addView(copy,LinearLayout.LayoutParams(0,-2,1f))
        line.addView(ui.actionIcon(R.drawable.ic_delete,"Delete conversation") {
            AlertDialog.Builder(activity).setTitle(ui.translate("Delete conversation"))
                .setMessage(ui.translate("This conversation will be removed from this device."))
                .setNegativeButton(ui.translate("Cancel"),null)
                .setPositiveButton(ui.translate("Delete")) { _,_->vm.deleteConversation(chat.id) }.show()
        })
        ui.clickable(line,ui.palette.surface) { vm.openConversation(chat.id) }
        add(line,gap=12)
    }
}
