package com.example.marvellobby.presentation

import android.widget.LinearLayout
import com.example.marvellobby.data.model.*
import com.example.marvellobby.data.repository.cleanCatalogText

fun ScreenRenderer.catalogDescription(entity: ComicEntity) {
    val summary=cleanCatalogText(entity.summary.orEmpty())
    val full=cleanCatalogText(entity.descriptionHtml.orEmpty()).ifBlank { summary }
    val pages=descriptionPages(full)
    if(pages.isEmpty()) { body("No description available.");return }
    val index=state.descriptionPage[state.route.key] ?: if(summary.isBlank())0 else -1
    val original="${entity.type}:${entity.id}" in state.originalDescriptions
    val source=if(index<0)descriptionPages(summary).firstOrNull().orEmpty() else pages[index.coerceIn(pages.indices)]
    val translated=if(state.preferences.language=="pt" && !original)state.translatedTexts[vm.translationKey(source)] else null
    label(if(index<0)"OVERVIEW" else ui.translate("Description")+" · ${index.coerceAtLeast(0)+1}/${pages.size}")
    add(ui.text(translated?.text ?: source,14,ui.palette.muted).apply {
        if(index<0) { maxLines=6;ellipsize=android.text.TextUtils.TruncateAt.END }
    })
    if(translated?.loading==true)body("Translating into Portuguese…")
    if(translated?.text!=null)add(ui.text("Translated with AI",11,ui.palette.muted))
    if(translated?.error!=null) {
        body("Translation unavailable. Showing the original text.")
        button("Retry translation",false) { vm.translateCatalog(source,true) }
    }
    if(state.preferences.language=="pt")button(if(original)"Translate into Portuguese" else "View original text",false) { vm.toggleOriginal(entity) }
    if(index<0) {
        if(full!=summary || pages.size>1 || summary.length>260)button("Read full description",false) { vm.descriptionPage(0) }
    } else {
        val controls=ui.row()
        if(index>0)controls.addView(ui.button("Previous",false) { vm.descriptionPage(index-1) },LinearLayout.LayoutParams(0,ui.dp(52),1f).apply { marginEnd=ui.dp(8) })
        if(index<pages.lastIndex)controls.addView(ui.button("Next",false) { vm.descriptionPage(index+1) },LinearLayout.LayoutParams(0,ui.dp(52),1f))
        if(controls.childCount>0)add(controls)
        if(summary.isNotBlank())button("Back to summary",false) { vm.descriptionPage(-1) }
    }
}
