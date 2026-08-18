package com.brain.offlineai.ui.tasks

/**
 * Phase 12 (Multi-task handling engine, Claude-style UI spec section 6) -
 * real, deterministic breakdown of a single user message into an ordered
 * list of distinct tasks.
 *
 * Per explicit user instruction this phase ("koi funsion torna nahin, koi
 * fake android api use nahin, koi dumy use nahin") this is genuine text
 * parsing against a fixed, documented rule set below - it deliberately
 * never asks the model to "guess" a breakdown. Feeding the raw message to
 * BrainEngine and asking it to split itself into tasks would be a real
 * `generate()` call, but its output could legitimately drift from what the
 * user actually wrote (reworded, merged, or invented steps) and then get
 * presented back to the user as if it were their own instruction split
 * apart - that is a correctness risk this phase avoids entirely by only
 * ever cutting the user's own literal text at real, unambiguous
 * boundaries.
 *
 * Rule A - explicit list (highest confidence). Two or more non-empty
 * lines, where *every* non-empty line matches a numbered (`1.`/`1)`) or
 * bulleted (`-`/`*`) prefix, are treated as one task per line, in that
 * order, prefix stripped. This is the unambiguous, spec-matching case: the
 * user already wrote a list.
 *
 * Rule B - explicit sequential connector (used only when Rule A finds
 * nothing). A single block of text is split on a literal, case-insensitive
 * connector phrase - " then ", " and then ", " after that " - and the
 * split is only ever kept as a real multi-task result when there are 2+
 * segments AND every segment has at least [MIN_WORDS] words. The word-
 * count floor exists specifically to reject false positives like "read and
 * write the config file" or "look at this then" (a trailing fragment isn't
 * a real second task) - an under-confident non-split is the safe failure
 * mode here, not an over-eager one.
 *
 * Anything matching neither rule returns a single-element list containing
 * the original, untouched text. Every caller must treat a size-1 result as
 * "not multi-task" and fall back to the existing single-task flow
 * unchanged - this is exactly how [com.brain.offlineai.ui.screens.chat.ChatViewModel.sendMessage]
 * uses it, so Phase 1-11 behavior for every ordinary single-instruction
 * message is completely unaffected (Document-Editing Convention).
 */
object TaskSplitter {
    private const val MIN_WORDS = 3
    private val wordSplitter = Regex("""\s+""")
    private val numberedOrBulletLine = Regex("""^\s*(?:\d+[.)]|[-*])\s+(.+)$""")
    private val sequentialConnectors = listOf(
        Regex("""(?i)\s+and\s+then\s+"""),
        Regex("""(?i)\s+after\s+that\s*,?\s+"""),
        Regex("""(?i)\s+then\s+""")
    )

    fun split(rawText: String): List<String> {
        val text = rawText.trim()
        if (text.isEmpty()) return listOf(text)

        val nonEmptyLines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (nonEmptyLines.size >= 2) {
            val listItems = nonEmptyLines.mapNotNull { line -> numberedOrBulletLine.find(line)?.groupValues?.get(1)?.trim() }
            if (listItems.size == nonEmptyLines.size) {
                return listItems
            }
        }

        for (connector in sequentialConnectors) {
            if (!connector.containsMatchIn(text)) continue
            val segments = text.split(connector).map { it.trim() }.filter { it.isNotEmpty() }
            val allSegmentsSubstantial = segments.all { segment -> segment.split(wordSplitter).size >= MIN_WORDS }
            if (segments.size >= 2 && allSegmentsSubstantial) {
                return segments
            }
        }

        return listOf(text)
    }
}
