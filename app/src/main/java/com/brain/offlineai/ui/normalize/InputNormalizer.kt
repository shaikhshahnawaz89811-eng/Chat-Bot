package com.brain.offlineai.ui.normalize

/**
 * Phase 13 (User-mistake / mixed-input normalization, Claude-style UI spec
 * section 7) - real, narrow, deterministic cleanup and ambiguity checks
 * run on a message's own literal text before it becomes a real prompt.
 *
 * Per explicit user instruction this phase ("koi funsion torna nahin, koi
 * fake android api use nahin, koi dumy use nahin") and the same
 * no-fabrication reasoning [com.brain.offlineai.ui.tasks.TaskSplitter]
 * already documents: this does not ask the model to "guess" what the user
 * meant to type. Feeding the raw message through a real generate() call to
 * "clean it up" could legitimately reword, drop, or invent content, and
 * then that altered text would get acted on as if it were the user's own
 * request. Everything below is instead a small, fixed set of safe text
 * transformations and pattern checks - real work, deliberately narrow (an
 * under-confident non-fix/non-flag is the safe failure mode here, same
 * philosophy TaskSplitter already applies).
 */
object InputNormalizer {

    /**
     * Real, safe cleanup only - nothing here can change what the user is
     * actually asking for, only how the raw characters are shaped:
     *  - collapses runs of spaces/tabs (never newlines, so a real list is
     *    still intact for [com.brain.offlineai.ui.tasks.TaskSplitter]'s
     *    own line-based Rule A) down to a single space per line,
     *  - normalizes common "smart" punctuation (curly quotes, en/em
     *    dashes) to their plain ASCII equivalents,
     *  - removes an immediate, case-insensitive duplicate word ("the the
     *    file" -> "the file") - a genuine, common real typing mistake
     *    with essentially zero risk of altering intent.
     */
    fun normalize(raw: String): String {
        val punctuationFixed = raw
            .replace('\u2018', '\'').replace('\u2019', '\'')
            .replace('\u201C', '"').replace('\u201D', '"')
            .replace('\u2013', '-').replace('\u2014', '-')

        val lines = punctuationFixed.split("\n").map { line ->
            collapseDuplicateWords(line.replace(Regex("""[ \t]+"""), " ").trim())
        }
        return lines.joinToString("\n").trim()
    }

    private fun collapseDuplicateWords(line: String): String {
        val words = line.split(" ")
        if (words.size < 2) return line
        val result = StringBuilder()
        var previousComparable: String? = null
        for (word in words) {
            val comparable = word.trim(',', '.', '!', '?').lowercase()
            if (previousComparable != null && comparable.isNotEmpty() && comparable == previousComparable) {
                continue
            }
            if (result.isNotEmpty()) result.append(' ')
            result.append(word)
            previousComparable = comparable
        }
        return result.toString()
    }

    /**
     * Fixed, small set of common direct-opposite verb pairs - the one real
     * kind of "conflicting instruction" this app can honestly detect
     * without asking the model to interpret intent: both a word and its
     * listed direct opposite appearing as whole words in the same
     * message. General semantic contradiction detection is explicitly out
     * of scope (Rule 1 - no invented capability this app doesn't really
     * have): a false negative here just means a normal generation runs
     * (the safe outcome), a false positive would incorrectly block a real,
     * legitimate request, so the list stays small and unambiguous on
     * purpose.
     */
    private val oppositePairs = listOf(
        "delete" to "keep",
        "remove" to "add",
        "enable" to "disable",
        "start" to "stop",
        "show" to "hide",
        "allow" to "block",
        "increase" to "decrease",
        "expand" to "collapse"
    )

    data class ConflictInfo(val wordA: String, val wordB: String)

    fun detectConflict(text: String): ConflictInfo? {
        val lower = text.lowercase()
        for ((a, b) in oppositePairs) {
            val hasA = Regex("""\b${Regex.escape(a)}\b""").containsMatchIn(lower)
            val hasB = Regex("""\b${Regex.escape(b)}\b""").containsMatchIn(lower)
            if (hasA && hasB) return ConflictInfo(a, b)
        }
        return null
    }

    /**
     * Fixed, small set of vague referents/verbs with no concrete noun of
     * their own - "fix it", "make it better", "do that again". Real,
     * conservative on purpose: only fires when the *entire* (already-
     * normalized, whitespace-cleaned) message is built from these words
     * plus a short length cap, so a longer message that merely happens to
     * contain the word "it" somewhere is never flagged - this is a
     * genuine emptiness-of-referent check, not a keyword blocklist.
     */
    private val vagueWords = setOf(
        "it", "this", "that", "fix", "improve", "better", "again", "please",
        "the", "a", "an", "and", "make", "do", "that's", "thing", "one", "now"
    )
    private const val MAX_VAGUE_WORD_COUNT = 6

    fun isVagueRequest(normalizedText: String): Boolean {
        val words = normalizedText
            .lowercase()
            .split(Regex("""\s+"""))
            .map { it.trim(',', '.', '!', '?') }
            .filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > MAX_VAGUE_WORD_COUNT) return false
        return words.all { it in vagueWords }
    }
}
