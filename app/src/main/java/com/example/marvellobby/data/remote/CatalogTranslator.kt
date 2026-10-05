package com.example.marvellobby.data.remote

fun interface CatalogTranslator {
    suspend fun translatePortuguese(text: String): String
}
