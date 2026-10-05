package com.example.marvellobby.data.model

import java.text.Normalizer
import java.util.Locale
import java.util.UUID

object Usernames {
    @JvmStatic fun create(name: String): String {
        val stem=Normalizer.normalize(name,Normalizer.Form.NFD).replace(Regex("\\p{M}"),"")
            .lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"),"").take(15).ifBlank { "hero" }
        return stem+"_"+UUID.randomUUID().toString().replace("-","").take(8)
    }
    fun normalize(value: String)=value.trim().removePrefix("@").lowercase(Locale.ROOT)
    fun valid(value: String)=value.matches(Regex("[a-z0-9_]{3,24}")) && value.any { it in 'a'..'z' || it in '0'..'9' }
}
