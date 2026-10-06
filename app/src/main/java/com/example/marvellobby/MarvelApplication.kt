package com.example.marvellobby

import android.app.Application
import androidx.room.Room
import com.example.marvellobby.data.local.*
import com.example.marvellobby.data.repository.*
import com.example.marvellobby.data.remote.GroqTranslationClient
import com.example.marvellobby.data.remote.LobbyApi

class MarvelApplication : Application() {
    val database by lazy { Room.databaseBuilder(this,MarvelDatabase::class.java,"marvel.db").addMigrations(MarvelDatabase.MIGRATION_1_2,MarvelDatabase.MIGRATION_2_3,MarvelDatabase.MIGRATION_3_4,MarvelDatabase.MIGRATION_4_5).build() }
    val preferences by lazy { PreferencesStore(this) }
    val accounts by lazy { AccountRepository(database.archive(),preferences) }
    val onlineAccounts by lazy { OnlineAccountRepository(database.archive(),LobbyApi(BuildConfig.LOBBY_API_BASE_URL),OnlineTokenStore(this),avatars) }
    val community by lazy { CommunityRepository(onlineAccounts,avatars) }
    val publicFavorites by lazy { PublicFavoritesRepository(database.archive(),onlineAccounts) }
    val library by lazy { LibraryRepository(database.archive()) }
    val marvel by lazy { MarvelRepository(cacheDirectory=java.io.File(cacheDir,"catalog")) }
    val ai by lazy { AiRepository() }
    val aiContext by lazy { AiContextRepository(marvel) }
    val chats by lazy { ChatRepository(database.archive()) }
    val avatars by lazy { AvatarRepository(this) }
    val translations by lazy { CatalogTranslationRepository(GroqTranslationClient(),java.io.File(cacheDir,"translations")) }
}
