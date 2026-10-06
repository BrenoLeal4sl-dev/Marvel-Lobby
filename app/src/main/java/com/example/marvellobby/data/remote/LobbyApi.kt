package com.example.marvellobby.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI
import javax.net.ssl.HttpsURLConnection

interface LobbyTransport {
    val origin: String
    suspend fun request(method: String,path: String,body: JSONObject?=null,bearer: String?=null): JSONObject
}
class LobbyApi(baseUrl: String): LobbyTransport {
    override val origin=baseUrl.trim().trimEnd('/')
    override suspend fun request(method: String,path: String,body: JSONObject?,bearer: String?)=withContext(Dispatchers.IO) {
        val base=URI(origin)
        require(base.scheme=="https" && base.host!=null && base.userInfo==null && base.query==null && base.fragment==null && base.path in listOf("","/")) { "Configure an HTTPS address for the online service." }
        val relative=URI(path)
        require(relative.path.startsWith("/v1/") && !relative.path.contains("..") && relative.scheme==null && relative.host==null && relative.fragment==null)
        val connection=URI(origin+path).toURL().openConnection() as HttpsURLConnection
        try {
            connection.requestMethod=method;connection.connectTimeout=15_000;connection.readTimeout=30_000
            connection.instanceFollowRedirects=false
            connection.setRequestProperty("Accept","application/json")
            bearer?.let { connection.setRequestProperty("Authorization","Bearer $it") }
            if(body!=null) {
                connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status=connection.responseCode
            val stream=if(status in 200..299)connection.inputStream else connection.errorStream
            val text=stream?.bufferedReader()?.use { reader ->
                val output=StringBuilder();val buffer=CharArray(4096)
                while(true) {
                    val count=reader.read(buffer);if(count<0)break
                    val maximum=if(relative.path.startsWith("/v1/archive"))1_000_000 else if(relative.path.startsWith("/v1/community/"))256_000 else 64_000
                    if(output.length+count>maximum)throw java.io.IOException("Invalid online service response.")
                    output.append(buffer,0,count)
                }
                output.toString()
            }.orEmpty()
            if(status !in 200..299)throw LobbyProtocol.failure(status,text)
            if(status==204)JSONObject() else JSONObject(text)
        } finally { connection.disconnect() }
    }
}
