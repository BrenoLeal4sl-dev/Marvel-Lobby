package com.example.marvellobby.data.repository

import androidx.core.text.HtmlCompat
import com.example.marvellobby.data.remote.CatalogTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

fun cleanCatalogText(html: String): String=HtmlCompat.fromHtml(
    html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"),""),HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()

class CatalogTranslationRepository(private val translator: CatalogTranslator,private val directory: File) {
    private val translationLock=Mutex()
    fun key(source: String)=MessageDigest.getInstance("SHA-256").digest(("pt-v1:"+source).toByteArray())
        .joinToString("") { "%02x".format(it) }
    suspend fun portuguese(source: String): String=withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target=File(directory,key(source)+".txt")
        if(target.isFile && target.length()>0)return@withContext target.readText()
        translationLock.withLock {
            if(target.isFile && target.length()>0)return@withLock target.readText()
            ensureActive()
            val translated=translator.translatePortuguese(source)
            ensureActive()
            runCatching {
                val temp=File(target.path+".tmp")
                temp.writeText(translated)
                java.nio.file.Files.move(temp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                directory.listFiles()?.filter { it.extension=="txt" }?.sortedByDescending { it.lastModified() }?.drop(200)?.forEach { it.delete() }
            }
            translated
        }
    }
}
