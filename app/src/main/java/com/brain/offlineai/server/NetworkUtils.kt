package com.brain.offlineai.server

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Real best-effort read of this device's own LAN IPv4 address (the address
 * a paired companion app on another phone, e.g. Rani, would use to reach
 * the Local API Server now that it binds to "0.0.0.0" - see
 * [LocalApiServer]). Walks the actual [NetworkInterface] list the OS
 * reports - no hardcoded subnet guess, no fake placeholder IP. Returns
 * null (never a made-up address) when nothing usable is found, e.g. no
 * Wi-Fi/hotspot connection - the caller is expected to show that as
 * "Not connected to Wi-Fi", not silently hide it.
 */
object NetworkUtils {
    fun getLocalIpv4Address(): String? = try {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    } catch (e: Exception) {
        // Real failure (e.g. permission/IO issue enumerating interfaces) -
        // treated the same as "nothing found", never a fabricated address.
        null
    }
}
