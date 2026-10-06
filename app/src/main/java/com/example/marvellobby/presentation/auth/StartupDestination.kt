package com.example.marvellobby.presentation.auth

import com.example.marvellobby.data.local.AppPreferences
import com.example.marvellobby.presentation.Route

object StartupDestination {
    fun resolve(preferences:AppPreferences,authenticated:Boolean,restored:Route?=null):Route=when {
        !preferences.languageChosen -> Route("language")
        !preferences.onboarded -> Route("welcome")
        !authenticated -> Route("login")
        restored?.screen !in listOf(null,"splash","language","welcome","login","register") -> restored!!
        else -> Route("home")
    }
}
