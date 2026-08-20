package com.brain.offlineai.computebridge

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val SERVICE_TYPE = "_sa-compute._tcp"

/**
 * Finds Compute Bridge workers advertising on the current Wi-Fi/hotspot
 * network - the same "_sa-compute._tcp" NSD service type the Worker app's
 * network/WorkerAdvertiser.kt registers with `ComputeBridge-<workerId>` as
 * the service name. No manual IP entry anywhere in this file, matching the
 * architecture doc's "Manual IP typing ko avoid kiya jayega".
 *
 * Discovery never carries the pairing token by design (see
 * docs/protocol.md - only the QR payload does), so this is only used to
 * show which workers are currently reachable, not to pair one.
 */
class WorkerDiscovery(private val context: Context) {

    /** Live, deduplicated set of workers currently visible on the LAN.
     * More than one worker phone can appear here at once. */
    fun discover(): Flow<List<DiscoveredWorker>> = callbackFlow {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val found = LinkedHashMap<String, DiscoveredWorker>()

        fun emitCurrent() { trySend(found.values.toList()) }

        fun resolve(serviceInfo: NsdServiceInfo) {
            // A fresh ResolveListener per call - NsdManager rejects reusing
            // one listener for a second concurrent resolve.
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val workerId = info.serviceName.removePrefix("ComputeBridge-")
                    @Suppress("DEPRECATION")
                    val host = info.host?.hostAddress ?: return
                    found[workerId] = DiscoveredWorker(workerId, host, info.port)
                    emitCurrent()
                }
            }
            runCatching { nsd.resolveService(serviceInfo, listener) }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("_sa-compute")) resolve(serviceInfo)
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                found.remove(serviceInfo.serviceName.removePrefix("ComputeBridge-"))
                emitCurrent()
            }
        }

        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener) }
            .onFailure { close(it) }

        awaitClose { runCatching { nsd.stopServiceDiscovery(discoveryListener) } }
    }.distinctUntilChanged()
}
