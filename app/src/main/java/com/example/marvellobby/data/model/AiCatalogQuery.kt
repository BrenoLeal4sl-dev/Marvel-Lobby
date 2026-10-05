package com.example.marvellobby.data.model

import java.text.Normalizer
import java.util.Locale

/** Search vocabulary only. Identity, descriptions and relationships still come from Comic Vine. */
object AiCatalogQuery {
    data class Subject(val type: ResourceType,val name: String)
    private data class Alias(val subject: Subject,val words: List<String>)
    private fun tokens(value: String): List<String> = Normalizer.normalize(value,Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"),"").split(Regex("[^a-zA-Z0-9]+" )).filter { it.isNotBlank() }
    private fun words(value: String)=tokens(value).map { it.lowercase(Locale.ROOT) }

    private fun alias(type: ResourceType,name: String,vararg alternatives: String) =
        (listOf(name)+alternatives).map { Alias(Subject(type,name),words(it)) }

    private val aliases=buildList {
        val character=ResourceType.CHARACTER
        addAll(alias(character,"Spider-Man","Homem-Aranha","Homem Aranha","Peter Parker"))
        addAll(alias(character,"Iron Man","Homem de Ferro","Tony Stark"))
        addAll(alias(character,"Captain America","Capitão América","Steve Rogers"))
        addAll(alias(character,"Black Widow","Viúva Negra","Natasha Romanoff"))
        addAll(alias(character,"Black Panther","Pantera Negra","T'Challa"))
        addAll(alias(character,"Doctor Strange","Doutor Estranho","Dr Estranho","Stephen Strange"))
        addAll(alias(character,"Scarlet Witch","Feiticeira Escarlate","Wanda Maximoff"))
        addAll(alias(character,"Hawkeye","Gavião Arqueiro","Clint Barton"))
        addAll(alias(character,"Ant-Man","Homem-Formiga"))
        addAll(alias(character,"Wasp","Vespa"))
        addAll(alias(character,"Silver Surfer","Surfista Prateado"))
        addAll(alias(character,"Ghost Rider","Motoqueiro Fantasma"))
        addAll(alias(character,"Daredevil","Demolidor","Matt Murdock"))
        addAll(alias(character,"Punisher","Justiceiro","Frank Castle"))
        addAll(alias(character,"Vision","Visão"))
        addAll(alias(character,"Winter Soldier","Soldado Invernal","Bucky Barnes"))
        addAll(alias(character,"Falcon","Falcão","Sam Wilson"))
        addAll(alias(character,"Captain Marvel","Capitã Marvel","Carol Danvers"))
        addAll(alias(character,"Green Goblin","Duende Verde","Norman Osborn"))
        addAll(alias(character,"Doctor Octopus","Doutor Octopus","Dr Octopus"))
        addAll(alias(character,"Storm","Tempestade","Ororo Munroe"))
        addAll(alias(character,"Cyclops","Ciclope","Scott Summers"))
        addAll(alias(character,"Beast","Fera","Hank McCoy"))
        addAll(alias(character,"Nightcrawler","Noturno","Kurt Wagner"))
        addAll(alias(character,"Rogue","Vampira"))
        addAll(alias(character,"Colossus","Colosso"))
        addAll(alias(character,"Invisible Woman","Mulher Invisível","Sue Storm"))
        addAll(alias(character,"Human Torch","Tocha Humana","Johnny Storm"))
        addAll(alias(character,"Mister Fantastic","Senhor Fantástico","Reed Richards"))
        addAll(alias(character,"Thing","Coisa","Ben Grimm"))
        addAll(alias(character,"Doctor Doom","Doutor Destino","Victor Von Doom"))
        addAll(alias(character,"Moon Knight","Cavaleiro da Lua"))
        addAll(alias(character,"Star-Lord","Senhor das Estrelas","Peter Quill"))
        addAll(alias(character,"Red Skull","Caveira Vermelha"))
        addAll(alias(character,"She-Hulk","Mulher-Hulk"))
        addAll(alias(character,"Luke Cage","Power Man"))
        addAll(alias(character,"Iron Fist","Punho de Ferro"))
        addAll(alias(character,"Quicksilver","Mercúrio","Pietro Maximoff"))
        addAll(alias(character,"Professor X","Professor Xavier","Charles Xavier"))
        addAll(alias(character,"Sabretooth","Dentes de Sabre"))
        addAll(alias(character,"Juggernaut","Fanático"))
        addAll(alias(character,"Miles Morales","Homem-Aranha Miles Morales"))
        listOf("Wolverine","Hulk","Thor","Loki","Thanos","Deadpool","Venom","Gambit","Jean Grey","Miles Morales")
            .forEach { addAll(alias(character,it)) }
        addAll(alias(ResourceType.TEAM,"Avengers","Vingadores"))
        addAll(alias(ResourceType.TEAM,"X-Men","X Men"))
        addAll(alias(ResourceType.TEAM,"Fantastic Four","Quarteto Fantástico"))
        addAll(alias(ResourceType.TEAM,"Guardians of the Galaxy","Guardiões da Galáxia"))
        addAll(alias(ResourceType.TEAM,"Defenders","Defensores"))
        addAll(alias(ResourceType.TEAM,"Sinister Six","Sexteto Sinistro"))
        addAll(alias(ResourceType.POWER,"Healing","Regeneration","Regeneração","Fator de cura"))
        addAll(alias(ResourceType.POWER,"Telepathy","Telepatia"))
        addAll(alias(ResourceType.POWER,"Teleport","Teleportation","Teletransporte"))
        addAll(alias(ResourceType.POWER,"Super Strength","Superforça","Super força"))
        addAll(alias(ResourceType.STORY_ARC,"Civil War","Guerra Civil"))
        addAll(alias(ResourceType.STORY_ARC,"Secret Wars","Guerras Secretas"))
        addAll(alias(ResourceType.STORY_ARC,"Infinity Gauntlet","Desafio Infinito"))
    }

    fun subjects(question: String): List<Subject> {
        val original=tokens(question)
        val tokens=original.map { it.lowercase(Locale.ROOT) }
        val bareSubject=words(searchText(question))
        val matches=mutableListOf<Subject>()
        var index=0
        while(index<tokens.size) {
            val match=aliases.filter { candidate ->
                index+candidate.words.size<=tokens.size &&
                    tokens.subList(index,index+candidate.words.size)==candidate.words
            }.maxByOrNull { it.words.size }
            if(match==null)index++ else {
                // Don't mistake "sua visão sobre essa coisa" for Vision and Thing.
                val generic=match.subject.name in setOf("Thing","Vision","Beast","Storm","Rogue","Wasp","Falcon","Defenders")
                val namedReference=index>0 && tokens[index-1] in setOf("do","da","dos","das","of")
                if(!generic || original[index].first().isUpperCase() || namedReference || bareSubject==match.words)matches.add(match.subject)
                index+=match.words.size
            }
        }
        return matches.distinct()
    }

    private val filler=Regex("(?i)\\b(quem|e|foi|era|sao|o|a|os|as|um|uma|me|explique|sobre|quais|qual|conte|fale|do|da|dos|das|who|is|was|are|what|tell|about|the|explain)\\b")
    fun searchText(question: String): String {
        val clean=Normalizer.normalize(question,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
        return clean.replace(filler," ").replace(Regex("[^\\p{L}\\p{N}\\s-]")," ")
            .replace(Regex("\\s+")," ").trim().take(100)
    }

    fun isFollowUp(question: String): Boolean {
        val tokens=words(question)
        return tokens.any { it in setOf("ele","ela","eles","elas","dele","dela","deles","delas","seu","sua","seus","suas","he","she","him","her","his","their","they","them","it","its") } ||
            searchText(question).lowercase(Locale.ROOT) in setOf("powers","poderes","abilities","habilidades","origin","origem","teams","times","equipes","primeira aparicao","first appearance")
    }
}
