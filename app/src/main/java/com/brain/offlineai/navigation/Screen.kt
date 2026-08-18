package com.brain.offlineai.navigation

/**
 * Every destination in the app. Route strings match the 7-phase plan in
 * PROGRESS.md. Screens whose route already exists but whose real content
 * ships in a later phase are marked accordingly — the route/entry exists
 * now (Rule 1 endpoint) even though full functionality lands later, so
 * nothing here is an orphan destination.
 */
sealed class Screen(val route: String, val label: String) {
    // Bottom nav (matches phone screens 1-4 bottom bar: Chat / History / Models / Settings)
    data object Chat : Screen("chat", "Chat")
    data object History : Screen("history", "History")
    data object Models : Screen("models", "Models") // import/load/unload: Phase 2. Settings sliders: Phase 5 (DONE)
    data object Settings : Screen("settings", "Settings") // screen 12, General Settings - Phase 6 (DONE)

    // Drawer-only destinations (matches left sidebar in mockup)
    data object ApiKeys : Screen("api_keys", "API Keys") // Phase 3 (DONE) - screen 5, list
    data object LocalApi : Screen("local_api", "Local API") // Phase 4 (DONE) - screen 10, connection status
    data object Analytics : Screen("analytics", "Analytics") // Phase 5 (DONE) - real usage counters
    data object About : Screen("about", "About") // screen 14 - Phase 6 (DONE)

    // Phase 5 sub-destination (screen 11) - reached only from the Models
    // screen's settings action, so it's not in drawerItems/bottomNavItems.
    data object ModelSettings : Screen("model_settings", "Model Settings")

    // Phase 6 sub-destination (screen 13, "Storage") - reached only from
    // the General Settings screen's own "Storage" row, same pattern as
    // ModelSettings above, so it's not in drawerItems/bottomNavItems.
    data object Storage : Screen("storage", "Storage")

    // Phase 7 sub-destination - reached only from a row on the real
    // History screen (Screen.History above), reopening one persisted
    // chat session in ChatScreen. Not in drawerItems/bottomNavItems,
    // same pattern as ModelSettings/Storage above.
    data object ChatSession : Screen("chat_session/{sessionId}", "Conversation") {
        fun routeFor(sessionId: String) = "chat_session/$sessionId"
    }

    // Phase 3 sub-destinations (screens 6-9) - reached only from within the
    // API Keys flow, so they're not in drawerItems/bottomNavItems below.
    data object ApiKeyCreate : Screen("api_key_create", "Create API Key")
    data object ApiKeyDetails : Screen("api_key_details/{keyId}", "Key Details") {
        fun routeFor(keyId: String) = "api_key_details/$keyId"
    }
    data object ApiKeyOptions : Screen("api_key_options/{keyId}", "Key Options") {
        fun routeFor(keyId: String) = "api_key_options/$keyId"
    }
    data object ApiKeyCopied : Screen("api_key_copied/{keyId}", "Copy Key") {
        fun routeFor(keyId: String) = "api_key_copied/$keyId"
    }

    companion object {
        // Lazy on purpose - see the fix note above the class. Building
        // these eagerly referenced sibling `data object`s of this same
        // sealed class, which crashes with a NullPointerException the
        // instant Screen.Chat is touched before every sibling singleton
        // has finished constructing (a classic JVM circular static-init
        // ordering issue, not a logic bug in the routes themselves).
        val bottomNavItems by lazy { listOf(Chat, History, Models, Settings) }
        val drawerItems by lazy { listOf(Chat, ApiKeys, Models, LocalApi, Analytics, Settings, About) }
    }
}
