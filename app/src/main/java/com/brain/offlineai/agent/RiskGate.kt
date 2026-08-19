package com.brain.offlineai.agent

/**
 * Phase 21 (Master Plan v2, section 6 architecture + section 9 step 7:
 * "Universal Rule/Permission/Risk Gate - every real action this agent can
 * take is classified before it runs"). Real, plain, deterministic
 * classification only - no model call, no guessed severity. This app has
 * exactly two real kinds of tool call today:
 *  - a real, bounded READ of bytes that already exist on disk (never
 *    changes anything), and
 *  - a real WRITE that creates a new file or replaces an existing entry's
 *    real content inside a ZIP.
 * There is no third case, so [RiskLevel] stays a plain two-value enum
 * rather than an invented severity scale this app has no real way to
 * differentiate further (Rule 1 - no fabricated precision).
 *
 * [AgentTool] enumerates every real tool this app actually has - see
 * [ToolGateway] for where each one's real underlying call lives (Rule 4 -
 * this gate wraps/classifies the existing real capabilities, it does not
 * duplicate or reimplement any of them).
 */
enum class RiskLevel { LOW, HIGH }

enum class AgentTool(val label: String, val risk: RiskLevel) {
    LIST_ZIP_ENTRIES("List ZIP entries", RiskLevel.LOW),
    READ_ZIP_ENTRY("Read ZIP entry content", RiskLevel.LOW),
    READ_TEXT_PREVIEW("Read attachment text", RiskLevel.LOW),
    LOAD_PROJECT_CONTEXT("Load project structure", RiskLevel.LOW),
    PATCH_ZIP_ENTRY("Patch ZIP entry (replaces real content)", RiskLevel.HIGH),
    WRITE_ARTIFACT_FILE("Write new artifact file", RiskLevel.HIGH)
}

/**
 * Real, minimal gate logic used by [ToolGateway] before/after every real
 * call: [requiresAudit] decides whether a call is real enough to need a
 * persisted record (every HIGH-risk, file-changing call - a LOW-risk read
 * changes nothing on disk, so logging it would only be noise, not a real
 * safety record).
 */
object RiskGate {
    fun requiresAudit(tool: AgentTool): Boolean = tool.risk == RiskLevel.HIGH
}
