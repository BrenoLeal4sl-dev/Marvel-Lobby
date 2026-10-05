package com.example.marvellobby.presentation

/** Navigation history contains routes only, never credentials, form values or chat messages. */
class AppNavigator {
    var current = Route("splash")
        private set
    private val history = mutableListOf<Route>()
    val backStack: List<Route> get() = history.toList()

    fun navigate(destination: Route, clearHistory: Boolean = false, replaceCurrent: Boolean = false): Route {
        val route = normalize(destination)
        if (clearHistory) history.clear()
        else if (!replaceCurrent && current != route && current.screen != "splash") history.add(current)
        current = route
        return current
    }

    fun back(authenticated: Boolean): Route? {
        if (current.screen == "splash") return current
        if (history.isNotEmpty()) {
            current = history.removeAt(history.lastIndex)
            return current
        }
        if (current.screen in setOf("home", "welcome", "login")) return null
        return navigate(Route(if (authenticated) "home" else "login"), clearHistory=true)
    }

    fun restore(destination: Route, previous: List<Route>): Route {
        history.clear()
        history.addAll(previous.filter { it.isRestorable() }.takeLast(100))
        current = normalize(destination.takeIf { it.isRestorable() } ?: Route("home"))
        return current
    }

    private fun Route.isRestorable(): Boolean = when(screen) {
        "publicProfile" -> userId?.let { runCatching { java.util.UUID.fromString(it).toString()==it }.getOrDefault(false) }==true
        "connectAccount" -> true
        "detail" -> type != null && id > 0
        "catalog" -> type != null
        "home", "explore", "search", "filters", "favorites", "ai", "chats", "profile",
        "editProfile", "settings", "preferences", "about", "privacy", "history" -> true
        else -> false
    }

    private fun normalize(destination: Route): Route {
        val section = when(destination.screen) {
            "home", "profile", "editProfile", "settings", "preferences", "about", "privacy", "history", "connectAccount", "publicProfile" -> "home"
            "explore", "catalog", "search", "filters" -> "explore"
            "favorites" -> "favorites"
            "ai", "chats" -> "ai"
            else -> destination.section ?: current.section ?: "home"
        }
        return destination.copy(section=section)
    }
}
