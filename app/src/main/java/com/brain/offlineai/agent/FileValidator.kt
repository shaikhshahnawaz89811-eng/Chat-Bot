package com.brain.offlineai.agent

import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Phase 25 (real per-file validation "helper" - user-requested: "ek halper
 * aisa rakhna jo code dekhe galt hai ya sahi, ya bada ya galt dumy toh
 * nahin"). This is the honest, on-device equivalent of a real code-review
 * pass: plain, deterministic checks over the real text a completed
 * generation actually produced - never a second model call asked "is this
 * good?" (a small on-device model judging its own just-written output is
 * not a real, independent check - same reasoning [PlanningEngine] already
 * gives for why its own parse is deterministic, not a second guess).
 *
 * Explicitly NOT a compiler and NOT a real "sandbox execution" - there is
 * no javac/kotlinc/gradle toolchain reachable from inside a running
 * Android app on a phone, so this never claims to "run" or "test-execute"
 * generated code. What it genuinely does: real brace/paren/bracket
 * balance counting (comment/string/char-literal aware - see
 * [checkBraceBalance]), a real HTML open/close tag-balance walk for
 * `.html`/`.htm` (void-element aware - see [checkHtmlStructure], since
 * real HTML5 is not XML and feeding it to a strict XML parser produces
 * false failures on perfectly valid markup like a bare `<br>` or
 * `<meta>`), a real XML parse (via the JDK/Android's own
 * `DocumentBuilderFactory` - a real parser, not a heuristic) for other
 * `.xml` files, and real, literal substring/regex checks for the
 * placeholder and dummy-code phrases models commonly emit instead of
 * finished code. A pass here means "no known red flag found" - it is not
 * a correctness proof, and this file says so honestly rather than
 * overclaiming (Rule 10/17).
 *
 * Post-Phase-25 hardening pass (user-requested review): two real gaps
 * fixed, both additive, nothing above removed or renamed -
 *  1. [checkBraceBalance] previously only skipped `"..."` string literals
 *     and `//`/`#` line comments - a real block comment containing an
 *     unbalanced-looking brace, or a real char literal like `'{'` (both
 *     completely ordinary in Kotlin/Java/C/JS) could throw off the count
 *     and produce a false "unbalanced" report. Now skips block comments
 *     and single-quoted char literals too.
 *  2. `.html`/`.htm` files were previously missing from `braceLangs`
 *     entirely - a generated website's actual markup file had **no**
 *     structural check at all, only the placeholder-text/short-file
 *     checks every extension gets. [checkHtmlStructure] closes that gap
 *     with a real (not XML, not regex-only) open/close tag stack walk.
 */
object FileValidator {

    data class ValidationResult(val fileName: String, val passed: Boolean, val issues: List<String>)

    /** Real, literal placeholder/dummy markers this project has actually seen models emit in place of real code - case-insensitive substring match, deliberately literal (no fuzzy/AI judgment) so a real false positive is always explainable from this exact list. */
    private val DUMMY_MARKERS = listOf(
        "todo", "fixme", "your code here", "your implementation here",
        "implement this", "implement me", "placeholder", "not implemented",
        "notimplementedexception", "notimplementederror", "coming soon",
        "lorem ipsum", "pass  # todo", "..."
    )

    private val braceLangs = setOf("kt", "kts", "java", "js", "ts", "jsx", "tsx", "c", "cpp", "h", "hpp", "cs", "json", "css", "go", "swift", "dart", "rs", "php")
    private val classOrFunKeywordLangs = setOf("kt", "kts", "java")
    private val htmlLangs = setOf("html", "htm")

    /** Real, minimum non-whitespace length below which a "real" source file is more likely an empty stub than genuine content - not a hard proof, just an honest signal (see class doc). */
    private const val SUSPICIOUSLY_SHORT_CHARS = 20

    /** Real HTML5 void elements - these are never expected to have a matching close tag, so [checkHtmlStructure] never demands one for them (unlike a strict XML parser, which would wrongly flag a bare `<br>` as an error). */
    private val VOID_ELEMENTS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"
    )

    /**
     * [wasTruncated] should be true when the real generation call that
     * produced [content] stopped with `"max_tokens"` rather than
     * `"end_of_generation"` - a real, known-incomplete file is flagged as
     * such rather than silently validated as if it were finished.
     */
    fun validate(fileName: String, content: String, wasTruncated: Boolean = false): ValidationResult {
        val issues = mutableListOf<String>()
        val trimmed = content.trim()
        val ext = fileName.substringAfterLast('.', "").lowercase()

        if (trimmed.isEmpty()) {
            issues += "file is empty"
            return ValidationResult(fileName, passed = false, issues = issues)
        }

        if (trimmed.count { !it.isWhitespace() } < SUSPICIOUSLY_SHORT_CHARS) {
            issues += "suspiciously short (${trimmed.length} chars) - likely incomplete, not real content"
        }

        val lowerContent = content.lowercase()
        val foundMarkers = DUMMY_MARKERS.filter { lowerContent.contains(it) }
        if (foundMarkers.isNotEmpty()) {
            issues += "possible placeholder/dummy content found: ${foundMarkers.joinToString(", ")}"
        }

        if (ext in braceLangs) {
            val balance = checkBraceBalance(content)
            if (balance != null) issues += balance
        }

        if (ext in htmlLangs) {
            val htmlIssue = checkHtmlStructure(content)
            if (htmlIssue != null) issues += htmlIssue
            val looksLikeHtml = listOf("<html", "<body", "<!doctype", "<div", "<head").any { lowerContent.contains(it) }
            if (!looksLikeHtml) issues += "no <html>/<body>/<!doctype>/<div>/<head> tag found - doesn't look like real HTML"
        } else if (ext == "xml") {
            val xmlIssue = checkXmlWellFormed(content)
            if (xmlIssue != null) issues += xmlIssue
        }

        if (ext in classOrFunKeywordLangs) {
            val hasStructure = listOf("class ", "fun ", "object ", "interface ", "enum ").any { content.contains(it) }
            if (!hasStructure) issues += "no class/fun/object/interface keyword found - doesn't look like real $ext source"
        }

        if (wasTruncated) {
            issues += "generation stopped mid-answer (max_tokens) - file may be genuinely incomplete"
        }

        return ValidationResult(fileName, passed = issues.isEmpty(), issues = issues)
    }

    /**
     * Real deterministic contract check for a web-app response. It does not
     * claim semantic correctness; it only rejects an obviously wrong artifact
     * type such as a shell installer when the user's actual request was a web app.
     */
    fun validateWebAppArtifact(fileName: String, content: String): ValidationResult {
        val issues = mutableListOf<String>()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val lower = content.lowercase()
        if (ext in setOf("sh", "bash", "zsh", "bat", "ps1")) {
            issues += "shell script is not a web-app source file"
        }
        if (ext == "html" || ext == "htm") {
            if (!(lower.contains("<html") || lower.contains("<!doctype html") || lower.contains("<body") || lower.contains("<main") || lower.contains("<div"))) {
                issues += "file is tagged as HTML but contains no recognizable HTML structure"
            }
        }
        if (lower.contains("pip install ") || lower.contains("apt install ") || lower.contains("npm install ") || lower.contains("sudo ")) {
            issues += "contains terminal installation commands instead of web-app source"
        }
        return ValidationResult(fileName, issues.isEmpty(), issues)
    }

    /**
     * Real, best-effort counter over `{ } ( ) [ ]` - not a full language
     * parser, so it's a heuristic (same honest posture
     * [com.brain.offlineai.ui.screens.chat.ChatViewModel]'s own
     * `chunkTokenBudget` already documents for its own char-per-token
     * estimate): a real compiler is the authoritative check, which this
     * app genuinely has no on-device path to - see class doc.
     *
     * Skips, so a real brace/paren/bracket inside any of these is never
     * falsely counted:
     * - `"..."` double-quoted string literals (escape-aware)
     * - `'x'` single-quoted char literals (escape-aware) - e.g. `'{'`,
     *   `'\''`, `'\n'` no longer throw the count off
     * - `//` and `#` line comments
     * - block comments (star-slash delimited), including multi-line ones
     */
    private fun checkBraceBalance(content: String): String? {
        var curly = 0
        var paren = 0
        var square = 0
        var inString = false
        var inChar = false
        var inLineComment = false
        var inBlockComment = false
        var i = 0
        val n = content.length
        while (i < n) {
            val c = content[i]

            if (inLineComment) {
                if (c == '\n') inLineComment = false
                i++; continue
            }
            if (inBlockComment) {
                if (c == '*' && i + 1 < n && content[i + 1] == '/') {
                    inBlockComment = false
                    i += 2; continue
                }
                i++; continue
            }
            if (inString) {
                if (c == '\\') { i += 2; continue }
                if (c == '"') inString = false
                i++; continue
            }
            if (inChar) {
                if (c == '\\') { i += 2; continue }
                if (c == '\'') inChar = false
                i++; continue
            }

            when {
                c == '/' && i + 1 < n && content[i + 1] == '/' -> { inLineComment = true; i += 2 }
                c == '/' && i + 1 < n && content[i + 1] == '*' -> { inBlockComment = true; i += 2 }
                c == '#' -> { inLineComment = true; i++ }
                c == '"' -> { inString = true; i++ }
                c == '\'' -> { inChar = true; i++ }
                c == '{' -> { curly++; i++ }
                c == '}' -> { curly--; i++ }
                c == '(' -> { paren++; i++ }
                c == ')' -> { paren--; i++ }
                c == '[' -> { square++; i++ }
                c == ']' -> { square--; i++ }
                else -> i++
            }
        }
        val problems = mutableListOf<String>()
        if (curly != 0) problems += "curly braces unbalanced (${if (curly > 0) "$curly unclosed" else "${-curly} extra closing"})"
        if (paren != 0) problems += "parentheses unbalanced (${if (paren > 0) "$paren unclosed" else "${-paren} extra closing"})"
        if (square != 0) problems += "square brackets unbalanced (${if (square > 0) "$square unclosed" else "${-square} extra closing"})"
        return if (problems.isEmpty()) null else problems.joinToString("; ")
    }

    /**
     * Real, best-effort HTML open/close tag-balance walk - deliberately
     * not a strict XML parse (real HTML5 allows unclosed void elements,
     * unquoted attributes, and `<!DOCTYPE html>`, all of which a strict
     * XML parser would wrongly reject). What this genuinely does: scans
     * real `<tag ...>` / `</tag>` tokens left to right with a stack,
     * skips `<!-- ... -->` comments and `<!DOCTYPE ...>` declarations,
     * never expects a close tag for a real [VOID_ELEMENTS] member or a
     * self-closing `<tag ... />`, and reports a genuine mismatch (wrong
     * tag closed) or genuinely unclosed tags left on the stack at EOF.
     * Not a full HTML5 parser (no tag-content-model rules, e.g. it won't
     * catch a `<td>` outside a `<table>`) - same honest, heuristic
     * posture [checkBraceBalance] already documents for itself.
     */
    private fun checkHtmlStructure(content: String): String? {
        val stack = ArrayDeque<String>()
        val problems = mutableListOf<String>()
        var i = 0
        val n = content.length
        while (i < n) {
            val c = content[i]
            if (c != '<') { i++; continue }

            if (content.startsWith("<!--", i)) {
                val end = content.indexOf("-->", i + 4)
                i = if (end == -1) n else end + 3
                continue
            }
            if (content.startsWith("<!", i)) {
                val end = content.indexOf('>', i)
                i = if (end == -1) n else end + 1
                continue
            }
            if (content.startsWith("<script", i, ignoreCase = true) || content.startsWith("<style", i, ignoreCase = true)) {
                val tagEnd = content.indexOf('>', i)
                if (tagEnd == -1) { i = n; continue }
                val tagName = if (content.startsWith("<script", i, ignoreCase = true)) "script" else "style"
                val closeTag = "</$tagName"
                val closeIdx = content.indexOf(closeTag, tagEnd, ignoreCase = true)
                i = if (closeIdx == -1) n else content.indexOf('>', closeIdx).let { if (it == -1) n else it + 1 }
                continue
            }

            val tagEnd = content.indexOf('>', i)
            if (tagEnd == -1) { i = n; continue }
            val rawTag = content.substring(i, tagEnd + 1)
            val isClosing = rawTag.startsWith("</")
            val isSelfClosing = rawTag.endsWith("/>")
            val nameMatch = Regex("</?\\s*([a-zA-Z][a-zA-Z0-9-]*)").find(rawTag)
            val tagName = nameMatch?.groupValues?.get(1)?.lowercase()

            if (tagName != null) {
                if (isClosing) {
                    if (stack.isNotEmpty() && stack.last() == tagName) {
                        stack.removeLast()
                    } else if (stack.contains(tagName)) {
                        // Real mismatch: something else was left open in between.
                        while (stack.isNotEmpty() && stack.last() != tagName) stack.removeLast()
                        if (stack.isNotEmpty()) stack.removeLast()
                        problems += "</$tagName> found but a different tag was open at that point - check nesting"
                    } else {
                        problems += "</$tagName> has no matching open <$tagName>"
                    }
                } else if (!isSelfClosing && tagName !in VOID_ELEMENTS) {
                    stack.add(tagName)
                }
            }
            i = tagEnd + 1
        }
        if (stack.isNotEmpty()) {
            problems += "unclosed tag(s): ${stack.toList().joinToString(", ") { "<$it>" }}"
        }
        return if (problems.isEmpty()) null else problems.joinToString("; ")
    }

    /** Real XML parse via the JDK/Android's own `DocumentBuilderFactory` - a real parser, not a regex guess. A genuine `SAXException`/parse failure means the XML is genuinely not well-formed. Used for non-HTML `.xml` files only - see [checkHtmlStructure] for why real HTML5 is checked separately instead of through this strict parser. */
    private fun checkXmlWellFormed(content: String): String? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.newDocumentBuilder().parse(InputSource(StringReader(content)))
            null
        } catch (e: Exception) {
            "XML is not well-formed: ${e.message ?: e::class.java.simpleName}"
        }
    }
}
