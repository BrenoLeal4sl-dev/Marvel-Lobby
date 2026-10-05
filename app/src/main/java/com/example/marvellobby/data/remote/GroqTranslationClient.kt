package com.example.marvellobby.data.remote

import com.example.marvellobby.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/** Only catalog excerpts are sent to Groq; Marvel AI conversations stay with Gemini. */
class GroqTranslationClient : CatalogTranslator {
    override suspend fun translatePortuguese(text: String): String = withContext(Dispatchers.IO) {
        if(BuildConfig.GROQ_API_KEY.isBlank())throw IOException("Configure GROQ_API_KEY in local.properties and rebuild.")
        val body=GroqTranslationProtocol.request(text,BuildConfig.GROQ_TRANSLATION_MODEL)
        val connection=URI("https://api.groq.com/openai/v1/chat/completions").toURL().openConnection() as HttpsURLConnection
        try {
            ensureActive()
            connection.requestMethod="POST";connection.doOutput=true
            connection.connectTimeout=15_000;connection.readTimeout=60_000
            connection.instanceFollowRedirects=false
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
            connection.setRequestProperty("Authorization","Bearer ${BuildConfig.GROQ_API_KEY}")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status=connection.responseCode
            if(status !in 200..299)throw IOException(GroqTranslationProtocol.failure(status))
            val response=connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            ensureActive()
            GroqTranslationProtocol.translation(response)
        } finally { connection.disconnect() }
    }
}
