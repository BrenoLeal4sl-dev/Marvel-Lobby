package com.example.marvellobby.data.remote

import com.example.marvellobby.data.model.*
import com.google.gson.JsonParser
import java.io.IOException
import java.net.URI

object PublicFavoritesProtocol {
    fun page(text: String,type: ResourceType,offset: Int): PublicFavoritesPage=try {
        val json=JsonParser.parseString(text).asJsonObject
        require(json.get("visible").asJsonPrimitive.isBoolean)
        val visible=json.get("visible").asBoolean
        val items=json.getAsJsonArray("items")
        require(items.size()<=12)
        val records=items.map { element ->
            val row=element.asJsonObject
            require(row.get("id").asJsonPrimitive.isNumber)
            val id=row.get("id").asBigDecimal.intValueExact();val name=row.get("name").asString
            require(row.get("type").asString==type.resource && id>0 && name.isNotBlank() && name.codePointCount(0,name.length)<=200)
            val image=row.get("imageUrl")?.takeUnless { it.isJsonNull }?.asString?.takeIf {
                val uri=URI(it);uri.scheme=="https" && uri.rawUserInfo==null && uri.port==-1 &&
                    uri.host?.matches(Regex("(comicvine\\.gamespot\\.com|comicvine\\d*\\.cbsistatic\\.com|static\\.comicvine\\.com)"))==true
            }
            PublicFavorite(id,type,name,image)
        }
        val next=json.get("next")?.takeUnless { it.isJsonNull }?.let { require(it.asJsonPrimitive.isNumber);it.asBigDecimal.intValueExact() }
        require(next==null || (next>offset && next<=100000 && records.size==12))
        require(visible || (records.isEmpty() && next==null))
        require(records.distinctBy { it.id }.size==records.size)
        PublicFavoritesPage(records,next,visible)
    } catch(error: Exception) { throw IOException("Invalid public favorites response.",error) }
}
