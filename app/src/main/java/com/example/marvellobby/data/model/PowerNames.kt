package com.example.marvellobby.data.model

/** Search aliases only; displayed names and IDs always come from Comic Vine. */
object PowerNames {
    fun query(value: String): String = when(value.trim().lowercase()) {
        "regeneration", "regeneração", "regeneracao", "healing factor" -> "Healing"
        "teleportation", "teletransporte" -> "Teleport"
        "energy projection", "projeção de energia" -> "Blast Power"
        "telepatia" -> "Telepathy"
        "voo", "vôo" -> "Flight"
        "superforça", "super força" -> "Super Strength"
        else -> value.trim()
    }
}
