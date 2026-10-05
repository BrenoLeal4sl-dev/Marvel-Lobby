package com.example.marvellobby.data.api

import com.example.marvellobby.data.model.ResourceType

/** Search uses the API search index (including aliases), rather than a literal name filter. */
internal object ComicVineRequests {
    const val RECENT = "date_last_updated:desc"
    data class Query(val path: String, val parameters: Map<String, String>)

    fun list(type: ResourceType, query: String, offset: Int, limit: Int, sort: String): Query {
        val parameters = linkedMapOf(
            "offset" to offset.toString(), "limit" to limit.toString(),
            "field_list" to "id,name,issue_number,volume,real_name,aliases,deck,image,publisher,origin,count_of_issue_appearances"
        )
        if (query.isNotBlank() && type in setOf(ResourceType.CHARACTER, ResourceType.TEAM)) {
            parameters["resources"] = type.resource
            parameters["query"] = query.trim().replace(Regex("\\s+"), " ")
            // Omitting sort preserves the search engine's relevance ranking.
            return Query("search/", parameters)
        }
        val supportedSort = sort.takeIf { it in setOf(RECENT,"name:asc","name:desc") } ?: RECENT
        parameters["sort"] = if (supportedSort == RECENT && type != ResourceType.CHARACTER) "name:asc" else supportedSort
        if (query.isNotBlank()) parameters["filter"] = "name:${query.trim()}"
        return Query("${type.collection}/", parameters)
    }
}

enum class ComicVineFailure {
    INVALID_KEY, ACCESS_BLOCKED, RATE_LIMIT, HTTP, API;

    val stopsRequests: Boolean get() = this in setOf(INVALID_KEY, ACCESS_BLOCKED, RATE_LIMIT)
}

internal object ComicVineErrors {
    fun classify(http: Int, apiStatus: Int?): ComicVineFailure? = when {
        http == 429 -> ComicVineFailure.RATE_LIMIT
        apiStatus == 100 -> ComicVineFailure.INVALID_KEY
        http == 401 -> ComicVineFailure.INVALID_KEY
        http == 403 -> ComicVineFailure.ACCESS_BLOCKED
        http != 200 -> ComicVineFailure.HTTP
        apiStatus != 1 -> ComicVineFailure.API
        else -> null
    }

    fun message(failure: ComicVineFailure, http: Int, apiStatus: Int?) = when (failure) {
        ComicVineFailure.INVALID_KEY -> "A Comic Vine não aceitou a credencial. Confira MARVEL_API_KEY no local.properties e recompile."
        ComicVineFailure.ACCESS_BLOCKED -> "A Comic Vine bloqueou esta conexão (HTTP 403). Isso não confirma uma chave inválida. Aguarde e tente novamente; se persistir, teste outra conexão."
        ComicVineFailure.RATE_LIMIT -> "Limite de consultas da Comic Vine atingido. Aguarde antes de tentar novamente."
        ComicVineFailure.HTTP -> "Não foi possível acessar a Comic Vine (HTTP $http)."
        ComicVineFailure.API -> "A Comic Vine não concluiu a consulta (código ${apiStatus ?: "indisponível"})."
    }
}
