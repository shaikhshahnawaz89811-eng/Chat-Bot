package com.brain.offlineai.data.apikeys

/** Options shown in the Create API Key expiration dropdown (mockup screen 6). */
enum class ExpirationOption(val label: String, val days: Long?) {
    NEVER("Never Expires", null),
    SEVEN_DAYS("7 Days", 7),
    THIRTY_DAYS("30 Days", 30),
    NINETY_DAYS("90 Days", 90);

    /** Real millisecond expiry computed from an actual creation timestamp, not a placeholder. */
    fun expiresAtFrom(createdAtMillis: Long): Long? =
        days?.let { createdAtMillis + it * 24 * 60 * 60 * 1000L }
}
