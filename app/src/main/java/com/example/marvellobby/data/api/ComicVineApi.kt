package com.example.marvellobby.data.api

import com.example.marvellobby.BuildConfig
import com.example.marvellobby.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ComicVineException(message: String, val failure: ComicVineFailure = ComicVineFailure.API,
                         val retryAfterSeconds: Long? = null) : IOException(message)

/** Network and parsing stay off the main thread. No credential logging. */
class ComicVineApi(private val apiKey: String = BuildConfig.MARVEL_API_KEY) : ComicVineSource {
    private val gate = ComicVineRequestGate()

    override suspend fun list(type: ResourceType, query: String, offset: Int, limit: Int, sort: String): ComicPage = withContext(Dispatchers.IO) {
        require(offset >= 0 && limit in 1..100)
        val querySpec = ComicVineRequests.list(type, query, offset, limit, sort)
        val body = request(querySpec.path, querySpec.parameters)
        val results = body.optJSONArray("results") ?: JSONArray()
        ComicPage(
            (0 until results.length()).map { parse(results.getJSONObject(it), type) },
            offset + body.optInt("number_of_page_results", results.length()),
            body.optInt("number_of_total_results", results.length()),
        )
    }

    override suspend fun detail(type: ResourceType, id: Int): ComicEntity = withContext(Dispatchers.IO) {
        require(id > 0)
        val fields="id,name,issue_number,volume,real_name,deck,description,image,publisher,origin,aliases,count_of_issue_appearances,first_appeared_in_issue,powers,teams,characters,character_credits,character_friends,story_arc_credits,team_credits,issues,issue_credits"
        parse(request("${type.resource}/${type.prefix}-$id/", mapOf("field_list" to fields)).getJSONObject("results"), type)
    }
    override suspend fun issues(ids: List<Int>): List<ComicEntity> = withContext(Dispatchers.IO) {
        val selected=ids.distinct()
        require(selected.size in 1..100 && selected.all { it>0 })
        val results=request("issues/",mapOf("filter" to "id:${selected.joinToString("|")}",
            "field_list" to "id,name,issue_number,volume,image", "limit" to selected.size.toString()))
            .optJSONArray("results") ?: JSONArray()
        (0 until results.length()).map { parse(results.getJSONObject(it),ResourceType.ISSUE) }
            .filter { it.id in selected }
    }

    private suspend fun request(path: String, params: Map<String, String>): JSONObject = gate.execute {
        if (apiKey.isBlank()) throw ComicVineException("Configure MARVEL_API_KEY no arquivo local.properties e recompile o aplicativo.")
        val query = (params + mapOf("api_key" to apiKey, "format" to "json")).entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val connection = URI("https://comicvine.gamespot.com/api/$path?$query").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "MarvelLobby-Android/1.0")
            connection.setRequestProperty("Accept", "application/json")
            // Do not forward the API key to a redirected host.
            connection.instanceFollowRedirects = false
            val http = connection.responseCode
            val text = (if (http == 200) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            // A firewall may return HTML: never show its body or mistake it for a key error.
            val body = runCatching { JSONObject(text) }.getOrNull()
            val apiStatus = body?.takeIf { it.has("status_code") }?.optInt("status_code")
            ComicVineErrors.classify(http, apiStatus)?.let { failure ->
                throw ComicVineException(ComicVineErrors.message(failure, http, apiStatus), failure,
                    connection.getHeaderField("Retry-After")?.toLongOrNull())
            }
            body!!
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(json: JSONObject, type: ResourceType) = ComicEntity(
        id = json.getInt("id"), type = type,
        name = json.text("name") ?: if(type==ResourceType.ISSUE) {
            listOfNotNull(json.optJSONObject("volume")?.text("name"),json.text("issue_number")?.let { "#$it" })
                .joinToString(" ").ifBlank { "Issue" }
        } else "Untitled",
        realName = json.text("real_name"), summary = json.text("deck"),
        descriptionHtml = json.text("description"),
        imageUrl = json.optJSONObject("image")?.let { it.text("super_url") ?: it.text("medium_url") }
            ?.replace("http://", "https://"),
        publisher = json.optJSONObject("publisher")?.reference(),
        appearanceCount = json.optInt("count_of_issue_appearances"),
        aliases = json.text("aliases"), origin = json.optJSONObject("origin")?.text("name"),
        firstIssue = json.optJSONObject("first_appeared_in_issue")?.reference()?.takeIf { it.id > 0 },
        powers = json.references("powers"), teams = (json.references("teams") + json.references("team_credits")).distinctBy { it.id },
        characters = (json.references("characters") + json.references("character_credits")).distinctBy { it.id },
        issues = (json.references("issues") + json.references("issue_credits")).distinctBy { it.id },
        friends = json.references("character_friends"), storyArcs = json.references("story_arc_credits"),
        issueNumber = json.text("issue_number"), volume = json.optJSONObject("volume")?.reference()?.takeIf { it.id > 0 },
        availableFields = json.keys().asSequence().filter { !json.isNull(it) }.toSet(),
    )

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    private fun JSONObject.reference() = ComicReference(optInt("id"), text("name") ?: "Sem título", text("issue_number"))
    private fun JSONObject.references(key: String): List<ComicReference> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it).reference() }.filter { it.id > 0 }.distinctBy { it.id }
    }
}
