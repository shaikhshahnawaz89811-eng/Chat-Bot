package com.brain.offlineai.data.apikeys

import java.security.SecureRandom

/**
 * Generates the actual secret value stored in `api_keys.keyValue`. Uses
 * SecureRandom (not Random/UUID) since this is a real credential the
 * companion Rani app will authenticate the local API server with in
 * Phase 4 - 24 bytes (192 bits) of entropy, hex-encoded, prefixed `brn_`
 * to match the mockup's "brn_9f8a7c2d3e4f5g6h7i8j9l0m" example.
 */
object KeyGenerator {
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        val hex = bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "brn_$hex"
    }
}
