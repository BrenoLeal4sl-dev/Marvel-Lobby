package com.example.marvellobby.data.model

enum class ResourceType(val collection: String, val resource: String, val prefix: Int) {
    CHARACTER("characters", "character", 4005),
    TEAM("teams", "team", 4060),
    ISSUE("issues", "issue", 4000),
    VOLUME("volumes", "volume", 4050),
    STORY_ARC("story_arcs", "story_arc", 4045),
    PUBLISHER("publishers", "publisher", 4010),
    POWER("powers", "power", 4035),
    LOCATION("locations", "location", 4020),
}

data class ComicReference(val id: Int, val name: String, val issueNumber: String? = null) {
    val displayName: String get() = issueNumber?.takeIf { it.isNotBlank() }?.let { "#$it · $name" } ?: name
    val hasTitle: Boolean get() = name.isNotBlank() && name.trim().lowercase(java.util.Locale.ROOT) !in setOf("sem título","untitled","issue","edição")
}

val ResourceType.canFavorite: Boolean get() = this in setOf(ResourceType.CHARACTER,ResourceType.TEAM,ResourceType.POWER,ResourceType.STORY_ARC)

data class ComicEntity(
    val id: Int,
    val type: ResourceType,
    val name: String,
    val realName: String?,
    val summary: String?,
    val descriptionHtml: String?,
    val imageUrl: String?,
    val publisher: ComicReference?,
    val appearanceCount: Int,
    val aliases: String?,
    val origin: String?,
    val firstIssue: ComicReference?,
    val powers: List<ComicReference>,
    val teams: List<ComicReference>,
    val characters: List<ComicReference>,
    val issues: List<ComicReference>,
    val friends: List<ComicReference> = emptyList(),
    val storyArcs: List<ComicReference> = emptyList(),
    val issueNumber: String? = null,
    val volume: ComicReference? = null,
    val availableFields: Set<String>? = null,
) {
    val isMarvel: Boolean get() = publisher?.id == 31 || publisher?.name == "Marvel Comics"
    val issueLabel: String? get() = if(type==ResourceType.ISSUE) {
        listOfNotNull(volume?.name,issueNumber?.let { "#$it" }).joinToString(" ").takeIf { it.isNotBlank() }
    } else null
    val characterRelationshipLabel: String get() = when(type) {
        ResourceType.POWER -> "CHARACTERS WITH THIS POWER"
        ResourceType.TEAM -> "MEMBERS / RELATED CHARACTERS"
        else -> "RELATED / CHARACTERS"
    }
}

/** Offset belongs to the unfiltered API page, not the Marvel-only visible results. */
data class ComicPage(val items: List<ComicEntity>, val nextOffset: Int, val total: Int, val offline: Boolean = false) {
    val hasMore: Boolean get() = nextOffset < total
}
