package com.example.marvellobby

import com.example.marvellobby.data.remote.GroqTranslationProtocol
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class GroqTranslationProtocolTest {
    @Test fun requestKeepsExcerptAsDataWithUnicodeAndQuotes() {
        val excerpt="Spider-Man is called \"Peter Parker\".\nHe protects São Paulo."
        val body=JsonParser.parseString(GroqTranslationProtocol.request(excerpt,"openai/gpt-oss-20b")).asJsonObject
        val messages=body.getAsJsonArray("messages")
        assertEquals(2,messages.size())
        assertEquals("system",messages[0].asJsonObject.get("role").asString)
        assertEquals("user",messages[1].asJsonObject.get("role").asString)
        assertEquals(excerpt,messages[1].asJsonObject.get("content").asString)
        assertFalse(body.has("api_key"))
        assertFalse(body.has("tools"))
        assertEquals(2048,body.get("max_completion_tokens").asInt)
        assertEquals("low",body.get("reasoning_effort").asString)
        assertFalse(body.get("include_reasoning").asBoolean)
    }
    @Test fun completePortugueseResponsePreservesParagraphs() {
        val json="""{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"  Spider-Man escala paredes.\n\nEle percebe o perigo.  "}}]}"""
        assertEquals("Spider-Man escala paredes.\n\nEle percebe o perigo.",GroqTranslationProtocol.translation(json))
    }
    @Test fun truncatedRefusedEmptyOrMalformedResponsesAreRejected() {
        listOf(
            """{"choices":[{"finish_reason":"length","message":{"content":"Tradução incompleta"}}]}""",
            """{"choices":[{"finish_reason":"content_filter","message":{"content":"Bloqueada"}}]}""",
            """{"choices":[{"finish_reason":"stop","message":{"content":null}}]}""",
            """{"choices":[]} """,
            "not json"
        ).forEach { response ->
            assertThrows(IOException::class.java) { GroqTranslationProtocol.translation(response) }
        }
    }
    @Test fun quotaAndAccessErrorsAreDistinctWithoutRawServerDetails() {
        assertTrue(GroqTranslationProtocol.failure(429).contains("limit"))
        assertTrue(GroqTranslationProtocol.failure(401).contains("refused"))
        assertTrue(GroqTranslationProtocol.failure(404).contains("model"))
        assertFalse(GroqTranslationProtocol.failure(500).contains("Gemini"))
    }
}
