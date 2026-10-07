package com.example.marvellobby.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("marvel_preferences")
data class AppPreferences(val onboarded: Boolean=false, val session: String="", val appearance: String="dark", val language: String="pt",val languageChosen:Boolean=false)
class PreferencesStore(private val context: Context) {
    fun riftTutorial(owner: String)=context.store.data.map { it[booleanPreferencesKey("rift_tutorial_"+owner)] ?: false }
    suspend fun finishRiftTutorial(owner: String) { context.store.edit { it[booleanPreferencesKey("rift_tutorial_"+owner)]=true } }
    private val onboarding = booleanPreferencesKey("onboarded")
    private val session = stringPreferencesKey("session")
    private val appearance = stringPreferencesKey("appearance")
    private val language = stringPreferencesKey("language")
    val flow = context.store.data.map {
        AppPreferences(it[onboarding] ?: false, it[session] ?: "", it[appearance] ?: "dark",
            it[language] ?: if(java.util.Locale.getDefault().language=="en")"en" else "pt",it[language]!=null)
    }
    suspend fun finishOnboarding() { context.store.edit { it[onboarding] = true } }
    suspend fun session(email: String) { context.store.edit { it[session] = email; it[onboarding] = true } }
    suspend fun appearance(value: String) { require(value in listOf("dark","light","system")); context.store.edit { it[appearance] = value } }
    suspend fun language(value: String) { require(value in listOf("en","pt")); context.store.edit { it[language] = value } }
}
