package com.example.marvellobby.data.model

/** Page at word boundaries; all source text remains available through Next/Previous. */
fun descriptionPages(source: String,limit: Int=1200): List<String> {
    require(limit>=100)
    var remaining=source.trim()
    val result=mutableListOf<String>()
    while(remaining.length>limit) {
        val paragraph=remaining.lastIndexOf("\n\n",limit)
        val word=remaining.lastIndexOf(' ',limit)
        var end=if(paragraph>=limit/2)paragraph else if(word>0)word else limit
        if(end>0 && Character.isHighSurrogate(remaining[end-1]))end--
        result.add(remaining.substring(0,end).trim())
        remaining=remaining.substring(end).trim()
    }
    if(remaining.isNotEmpty())result.add(remaining)
    return result
}
