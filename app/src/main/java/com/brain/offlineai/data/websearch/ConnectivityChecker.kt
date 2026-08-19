package com.brain.offlineai.data.websearch

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Real, current-moment check of whether this device genuinely has an
 * internet-capable network right now - the real Android
 * `ConnectivityManager`/`NetworkCapabilities` API every other real
 * connectivity check on this platform is built on, not a guessed/assumed
 * "probably online" default. This is the "offline-first, always" half of
 * Phase 22's own trigger rule (see PROGRESS.md's Phase 19 Plan section,
 * Phase 22 note): a real search is only ever attempted when this function
 * genuinely returns true.
 */
object ConnectivityChecker {
    fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
