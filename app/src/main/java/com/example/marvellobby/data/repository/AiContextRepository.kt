package com.example.marvellobby.data.repository

import com.example.marvellobby.data.model.AiCatalogQuery
import com.example.marvellobby.data.model.ComicEntity
import com.example.marvellobby.data.model.ResourceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Resolves translated search names before retrieval, without an extra model call. */
class AiContextRepository(private val catalog: MarvelRepository) {
    suspend fun records(question: String,selected: ComicEntity?,previous: List<ComicEntity>,
                        suggested: Pair<ResourceType,String>?=null): List<ComicEntity> {
        val subjects=if(suggested!=null)listOf(AiCatalogQuery.Subject(suggested.first,suggested.second))
            else AiCatalogQuery.subjects(question).take(3)
        val available=(listOfNotNull(selected)+previous).distinctBy { it.type to it.id }
        if(subjects.isNotEmpty())return subjects.mapNotNull { subject ->
            currentCoroutineContext().ensureActive()
            available.firstOrNull { it.type==subject.type && it.name.equals(subject.name,true) }
                ?: lookup(subject)
        }.distinctBy { it.type to it.id }
        if(AiCatalogQuery.isFollowUp(question) && available.isNotEmpty())return available.take(4)
        val query=AiCatalogQuery.searchText(question)
        // Keep the selected subject for questions about its properties, but allow explicit new subjects.
        val newSubject=Regex("(?i)^(quem|who)\\b").containsMatchIn(question.trim())
        if(selected!=null && !newSubject)return listOf(selected)
        if(query.isBlank())return available.take(4)
        return try { catalog.search(query).take(4) }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { emptyList() }
    }

    private suspend fun lookup(subject: AiCatalogQuery.Subject): ComicEntity? {
        val record=try {
            val items=catalog.browse(subject.type,subject.name).items
            items.find { it.name.equals(subject.name,true) } ?: items.firstOrNull()
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { return null }
        if(record==null)return null
        return try { catalog.detail(record.type,record.id) }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { record } // A blocked detail request must not discard the fetched summary.
    }
}
