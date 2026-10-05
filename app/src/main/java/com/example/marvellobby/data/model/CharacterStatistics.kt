package com.example.marvellobby.data.model

/** Counts of catalog records, never inferred combat abilities or official power rankings. */
data class CharacterStatistic(val label: String,val count: Int?)

fun ComicEntity.characterStatistics(): List<CharacterStatistic> {
    require(type==ResourceType.CHARACTER)
    fun known(value: Int,vararg fields: String): Int? {
        val present=availableFields
        // Old saved snapshots do not distinguish a missing field from an empty collection.
        return if(present?.any { it in fields } ?: (value>0))value.coerceAtLeast(0) else null
    }
    return listOf(
        CharacterStatistic("Issue appearances",known(appearanceCount,"count_of_issue_appearances")),
        CharacterStatistic("Registered powers",known(powers.distinctBy { it.id }.size,"powers")),
        CharacterStatistic("Related teams",known(teams.distinctBy { it.id }.size,"teams","team_credits")),
        CharacterStatistic("Related story arcs",known(storyArcs.distinctBy { it.id }.size,"story_arc_credits"))
    )
}

/** Both bars use the same maximum for their metric. Unknown counts remain unknown. */
fun comparisonFraction(value: Int?,other: Int?): Float? {
    if(value==null || other==null)return null
    val maximum=maxOf(value,other,0)
    return if(maximum==0)0f else value.coerceAtLeast(0).toFloat()/maximum
}
