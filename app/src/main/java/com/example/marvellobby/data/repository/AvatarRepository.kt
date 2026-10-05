package com.example.marvellobby.data.repository

import android.content.Context
import com.google.gson.Gson

data class MarvelAvatar(val id: Int,val name: String,val file: String,val source: String) {
    val uri: String get()="file:///android_asset/avatars/$file"
}
/** Approved original Comic Vine artwork, bundled so profile pictures also work offline. */
class AvatarRepository(context: Context) {
    val choices: List<MarvelAvatar> = context.assets.open("avatars/catalog.json").bufferedReader().use {
        Gson().fromJson(it.readText(),Array<MarvelAvatar>::class.java).toList()
    }
    fun approved(uri: String)=choices.any { it.uri==uri }
}
