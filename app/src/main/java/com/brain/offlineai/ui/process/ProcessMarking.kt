package com.brain.offlineai.ui.process

/**
 * Phase 8 (new spec) - the fixed marking set from the Claude-style UI spec
 * image, section 3 ("ALL MARKINGS - TYPES, ICONS & WHEN USED"). Every real
 * agent action in the app is reported to the user through exactly one of
 * these markings - nothing outside this list is invented ad hoc, and
 * nothing here is decorative-only: [LiveProcessCard] renders every value,
 * so adding a marking here without a real call site anywhere would be an
 * orphan per Rule 1/5.
 *
 * [icon] is a single emoji glyph (no icon-asset pipeline exists yet in
 * this project - matches the mockup's own emoji-style marks). [runningLabel]
 * is shown while the step is in progress (spinner/animated dots alongside
 * it); [completedLabel] replaces it once the step genuinely finishes.
 */
enum class ProcessMarking(
    val icon: String,
    val displayName: String,
    val runningLabel: String,
    val completedLabel: String
) {
    THINKING("\uD83E\uDDE0", "Thinking", "Thinking...", "Thinking complete"),
    PLANNING("\uD83D\uDCCB", "Planning", "Planning...", "Planning complete"),
    ANALYZING("\uD83D\uDD0D", "Analyzing", "Analyzing project...", "Project analyzed"),
    READING("\uD83D\uDCD6", "Reading", "Reading files...", "Read complete"),
    SEARCHING("\uD83C\uDF10", "Searching", "Searching web...", "Search complete"),
    FILE("\uD83D\uDCC1", "File", "Locating file...", "File located"),
    CREATING("\uD83D\uDCC4", "Creating", "Creating file...", "Created file"),
    EDITING("\u270F\uFE0F", "Editing", "Editing file...", "Edited file"),
    DELETING("\uD83D\uDDD1\uFE0F", "Deleting", "Removing file...", "Removed file"),
    WIRING("\uD83D\uDD0C", "Wiring", "Connecting...", "Connected"),
    INTEGRATING("\uD83E\uDDE9", "Integrating", "Integrating parts...", "Integrated"),
    TESTING("\uD83E\uDDEA", "Testing", "Testing application...", "Tests passed"),
    DEBUGGING("\uD83D\uDC1E", "Debugging", "Finding root cause...", "Root cause found"),
    FIXING("\uD83D\uDD27", "Fixing", "Fixing issue...", "Issue fixed"),
    VERIFYING("\u2705", "Verifying", "Verifying result...", "Result verified"),
    RECHECKING("\uD83D\uDD01", "Rechecking", "Rechecking...", "Recheck complete"),
    SAFETY_CHECK("\uD83D\uDEE1\uFE0F", "Safety Check", "Running safety check...", "Safety check passed"),
    PACKAGING("\uD83D\uDCE6", "Packaging", "Packaging project...", "Packaged"),
    ZIPPING("\uD83D\uDDDC\uFE0F", "Zipping", "Creating ZIP...", "ZIP ready"),
    UPLOADING("\u2B06\uFE0F", "Uploading", "Uploading file...", "Upload complete"),
    DOWNLOADING("\u2B07\uFE0F", "Downloading", "Preparing download...", "Ready to download"),
    SNAPSHOT("\uD83D\uDCF8", "Snapshot", "Saving snapshot...", "Snapshot saved"),
    DIFF("\uD83D\uDD00", "Diff", "Comparing changes...", "Changes compared"),
    ERROR("\u26A0\uFE0F", "Error", "Investigating issue...", "Issue fixed"),
    COMPLETE("\u2705", "Complete", "Finishing up...", "Complete")
}

/** A step's own running/complete/failed state - separate from which
 *  [ProcessMarking] it is, since the same marking can both succeed and
 *  fail (e.g. TESTING -> Tests passed, or TESTING -> Failed). */
enum class ProcessStepStatus { RUNNING, COMPLETE, FAILED }
