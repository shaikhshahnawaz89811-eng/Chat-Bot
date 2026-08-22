package com.brain.offlineai.agent

/**
 * Deterministic web-search planner. Web search is a tool, not a default
 * second brain: ordinary coding, project inspection, ZIP bug-fixing and
 * artifact operations stay local. A search is requested only when the user
 * explicitly asks for online/current/documentation information, or when a
 * brand-new creation request names a concrete build target and the search
 * can supply useful current/official implementation references.
 */
object WebSearchTrigger {

    private val CREATE_KEYWORDS = listOf(
        "build", "create", "make me", "develop", "generate", "banao", "banade", "bana do", "बना", "बनाओ"
    )

    private val BUILD_TARGET_WORDS = ProjectTypeGate.BUILD_TARGET_WORDS_PUBLIC

    private val EXPLICIT_WEB_SIGNALS = listOf(
        "search web", "web search", "search online", "look online", "look it up online",
        "search internet", "internet search", "online search", "find online", "web par search",
        "net par search", "google it", "latest", "current version", "current docs",
        "official docs", "official documentation", "documentation", "docs", "research online",
        "up to date", "up-to-date", "most recent", "newest"
    )

    private val INSPECTION_SEARCH_SIGNALS = listOf(
        "search web", "web search", "search online", "look online", "find online",
        "official docs", "official documentation", "documentation", "docs", "latest",
        "current version", "current api", "current sdk", "up to date", "up-to-date",
        "most recent", "newest"
    )

    data class SearchPlan(
        val query: String,
        val includeDomains: List<String> = emptyList()
    )

    fun newProjectSearchQuery(normalizedText: String): String? =
        plan(normalizedText, hasZipAttachment = false)?.query

    fun existingProjectSearchQuery(normalizedText: String, hasZipAttachment: Boolean): String? {
        if (!hasZipAttachment) return null
        val lower = normalizedText.lowercase()
        return if (INSPECTION_SEARCH_SIGNALS.any { lower.contains(it) }) {
            buildPlan(normalizedText, forExistingProject = true).query
        } else {
            null
        }
    }

    /** Kept for existing callers; unlike the old implementation this returns a focused query. */
    fun buildTargetSearchQuery(normalizedText: String): String? =
        plan(normalizedText, hasZipAttachment = false)?.query

    /** Single entry point used by ChatViewModel so trigger and query shaping cannot drift apart. */
    fun plan(normalizedText: String, hasZipAttachment: Boolean): SearchPlan? {
        val lower = normalizedText.lowercase()
        if (hasZipAttachment) {
            if (INSPECTION_SEARCH_SIGNALS.none { lower.contains(it) }) return null
            return buildPlan(normalizedText, forExistingProject = true)
        }

        val hasCreateIntent = CREATE_KEYWORDS.any { lower.contains(it) }
        val hasBuildTarget = BUILD_TARGET_WORDS.any { lower.contains(it) }
        val explicitlyWantsWeb = EXPLICIT_WEB_SIGNALS.any { lower.contains(it) }

        // An explicit web-search request is always eligible, even when it
        // is phrased as a research question rather than a build request.
        // Conversely, a creation target alone is NOT permission to search:
        // ordinary requests such as "create web app" stay local.
        if (explicitlyWantsWeb) {
            return buildPlan(normalizedText, forExistingProject = false)
        }
        if (!hasCreateIntent || !hasBuildTarget) return null
        return null
    }

    private fun buildPlan(text: String, forExistingProject: Boolean): SearchPlan {
        val lower = text.lowercase()
        val target = when {
            listOf("android", "apk", "jetpack compose", "kotlin android").any { lower.contains(it) } -> "Android Kotlin app development"
            listOf("flutter", "dart").any { lower.contains(it) } -> "Flutter Dart app development"
            listOf("react", "next.js", "nextjs").any { lower.contains(it) } -> "React web app development"
            listOf("vue", "nuxt").any { lower.contains(it) } -> "Vue web app development"
            listOf("node", "node.js", "nodejs").any { lower.contains(it) } -> "Node.js backend development"
            listOf("python", "django", "flask").any { lower.contains(it) } -> "Python web application development"
            listOf("ios", "swift").any { lower.contains(it) } -> "iOS Swift app development"
            "firebase" in lower -> "Firebase official documentation"
            "supabase" in lower -> "Supabase official documentation"
            "github api" in lower || "github" in lower -> "GitHub API official documentation"
            listOf("api", "backend", "server").any { lower.contains(it) } -> "backend API development"
            listOf("website", "web app", "webapp", "web site").any { lower.contains(it) } -> "modern web app development"
            else -> "software development"
        }

        val query = if (forExistingProject) {
            // Keep the user's concrete problem terms, but make the query a
            // documentation/reference lookup rather than sending the whole
            // ZIP-edit instruction verbatim to search.
            "$target ${searchProblemTerms(text)}"
        } else {
            // For a new-project request, preserve the user's actual technical
            // terms instead of searching only a generic phrase like
            // "modern web app development". This makes an explicit search
            // for "service worker", "Firebase auth", etc. actually retrieve
            // material about that requested part of the build.
            "$target ${searchProblemTerms(text)} official documentation current implementation guidance"
        }

        return SearchPlan(query = query.trim(), includeDomains = domainsFor(target))
    }

    private fun searchProblemTerms(text: String): String {
        val cleaned = text
            .replace(Regex("(?i)\\b(search web|web search|search online|look online|find online|latest|current version|official docs?|official documentation|up[- ]to[- ]date|most recent|newest)\\b"), " ")
            .replace(Regex("(?i)\\b(create|build|make|develop|generate|banao|banado|bana do)\\b"), " ")
            .replace(Regex("(?i)\\b(web app|webapp|website|web site|project|application|app)\\b"), " ")
            .replace(Regex("(?i)\\b[A-Za-z0-9._-]+\\.zip\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.take(180).ifBlank { "current web implementation guidance" }
    }

    private fun domainsFor(target: String): List<String> = when {
        target.startsWith("Android Kotlin") -> listOf("developer.android.com", "kotlinlang.org")
        target.startsWith("Flutter") -> listOf("docs.flutter.dev", "dart.dev")
        target.startsWith("React") -> listOf("react.dev", "vite.dev", "developer.mozilla.org")
        target.startsWith("Vue") -> listOf("vuejs.org", "vite.dev", "developer.mozilla.org")
        target.startsWith("Node.js") -> listOf("nodejs.org", "developer.mozilla.org")
        target.startsWith("Python") -> listOf("docs.python.org", "docs.djangoproject.com", "flask.palletsprojects.com")
        target.startsWith("iOS") -> listOf("developer.apple.com", "swift.org")
        target.startsWith("Firebase") -> listOf("firebase.google.com")
        target.startsWith("Supabase") -> listOf("supabase.com")
        target.startsWith("GitHub API") -> listOf("docs.github.com")
        target.startsWith("backend") -> listOf("developer.mozilla.org", "nodejs.org", "fastapi.tiangolo.com")
        target.startsWith("modern web") -> listOf("developer.mozilla.org", "web.dev", "vite.dev")
        else -> emptyList()
    }
}
