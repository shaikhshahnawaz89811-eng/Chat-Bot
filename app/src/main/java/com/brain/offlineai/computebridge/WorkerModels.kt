package com.brain.offlineai.computebridge

/**
 * Compute Bridge integration - lets this app hand a generation request to
 * another phone running the Compute Bridge Worker app instead of always
 * running llama.cpp locally (see docs/main-app-integration.md and
 * docs/protocol.md in the Worker app, and
 * Distributed_Compute_Bridge_Architecture.pdf's "Main APK - Kya Change
 * Hoga" table for the module breakdown this package implements).
 *
 * There is no physical RAM/CPU sharing anywhere in this package: a paired
 * worker runs the model on its own device using its own memory and
 * returns only the generated text over the local network. See
 * [ComputeManager]'s own doc for the routing behavior.
 */

/** One paired Compute Bridge worker device. [PairedWorkerStore] holds a
 * real list of these - this app is never limited to a single paired
 * worker. */
data class PairedWorker(
    val workerId: String,
    val host: String,
    val port: Int,
    val token: String,
    val label: String = workerId,
    val enabled: Boolean = true,
    /** Higher priority is tried first when more than one worker is
     * paired and enabled. */
    val priority: Int = 0
)

/** Local / Remote / Auto, matching the "Worker Settings" module in the
 * architecture doc. */
enum class ComputeMode { LOCAL, REMOTE, AUTO }

/** A worker seen advertising `_sa-compute._tcp` on the current Wi-Fi/
 * hotspot network, before it has been paired (no access token yet - NSD
 * discovery deliberately never carries the pairing token, only the QR
 * payload does). */
data class DiscoveredWorker(
    val workerId: String,
    val host: String,
    val port: Int
)
