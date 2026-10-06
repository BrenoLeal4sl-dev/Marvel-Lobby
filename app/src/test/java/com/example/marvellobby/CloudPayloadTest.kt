package com.example.marvellobby

import com.example.marvellobby.data.repository.CloudPayload
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class CloudPayloadTest {
    private val id="11111111-1111-4111-8111-111111111111"
    private fun chat(messages: String,context: String="null")="""{"id":"$id","title":"Spider-Man","messages":$messages,"context":$context,"sources":[]}"""
    @Test fun historySnapshotsKeepIdentityAndStripHtmlAndExternalImages() {
        val source="""{"entity":{"id":1443,"type":"CHARACTER","name":"Spider-Man","descriptionHtml":"<p>Huge HTML</p>","imageUrl":"https://example.invalid/tracking","powers":[],"teams":[]},"viewedAt":1000}"""
        val result=JsonParser.parseString(CloudPayload.compact("history:CHARACTER:1443",source)).asJsonObject.getAsJsonObject("entity")
        assertEquals(1443,result.get("id").asInt);assertNull(result.get("descriptionHtml"));assertNull(result.get("imageUrl"))
        assertThrows(IllegalArgumentException::class.java) { CloudPayload.compact("history:CHARACTER:1440",source) }
    }
    @Test fun onlyCommonPrefixesMergeAndDivergentRepliesGetASeparateUuid() {
        val start=chat("""[{"role":"user","text":"Who is Spider-Man?"}]""")
        val one=chat("""[{"role":"user","text":"Who is Spider-Man?"},{"role":"model","text":"Peter Parker."}]""")
        val two=chat("""[{"role":"user","text":"Who is Spider-Man?"},{"role":"model","text":"Miles Morales."}]""")
        assertTrue(CloudPayload.compatibleChats(start,one));assertFalse(CloudPayload.compatibleChats(one,two))
        val (key,fork)=CloudPayload.fork(two,"Conversa preservada")
        assertNotEquals("chat:$id",key);assertEquals(two.substringAfter("\"messages\":").substringBefore(",\"context\""),
            fork.substringAfter("\"messages\":").substringBefore(",\"context\""))
        assertEquals(fork,CloudPayload.compact(key,fork))
    }
    @Test fun unsupportedMessageRolesAreRejectedWithoutDroppingText() {
        assertThrows(IllegalArgumentException::class.java) { CloudPayload.compact("chat:$id",chat("""[{"role":"system","text":"malicious"}]""")) }
        val text="full answer ".repeat(1000)
        val payload=chat("""[{"role":"model","text":"$text"}]""")
        assertEquals(text,JsonParser.parseString(CloudPayload.compact("chat:$id",payload)).asJsonObject.getAsJsonArray("messages")[0].asJsonObject.get("text").asString)
    }
}
