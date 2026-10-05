package com.example.marvellobby.data.api

import com.example.marvellobby.data.model.ComicEntity
import com.example.marvellobby.data.model.ComicPage
import com.example.marvellobby.data.model.ResourceType

interface ComicVineSource {
    suspend fun list(type: ResourceType, query: String = "", offset: Int = 0,
                     limit: Int = 40, sort: String = "date_last_updated:desc"): ComicPage
    suspend fun detail(type: ResourceType, id: Int): ComicEntity
    suspend fun issues(ids: List<Int>): List<ComicEntity> = emptyList()
}
