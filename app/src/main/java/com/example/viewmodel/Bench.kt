package com.example.viewmodel

import android.os.SystemClock
import android.util.Log
import com.example.core.Category
import com.example.core.EmergencyRequest
import com.example.core.RequestStatus
import com.example.mesh.MeshTransportAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Every line looks like:
 *   BENCH  <ms since boot>|<nodeId>|<EVENT>|key=value|key=value
 * Filter logcat by the tag "BENCH".
 */
object BenchLog {
    const val TAG = "BENCH"

    @Volatile
    var node: String = "?"

    fun nowMs(): Double = SystemClock.elapsedRealtimeNanos() / 1_000_000.0

    fun log(event: String, vararg kv: Pair<String, Any?>) {
        val extras = kv.joinToString("|") {
            val v = it.second.toString().replace(Regex("\\s+"), "_").replace("|", "/")
            "${it.first}=$v"
        }
        Log.i(TAG, String.format(Locale.US, "%.3f", nowMs()) + "|$node|$event|$extras")
    }
}

/**
 * Sends special benchmark packets through the real mesh transport.
 * Benchmark packets are never stored in the DB or re-broadcast.
 *
 *  PING-<run>-<n>  : phone A sends, phone B instantly echoes PONG-<run>-<n>
 *  LOSS-<run>-<n>  : phone A sends a burst, phone B just logs what arrives
 */
class BenchRunner(
    private val transport: MeshTransportAdapter,
    private val scope: CoroutineScope,
    private val locationProvider: LocationProvider,
    private val nodeId: String
) {
    init {
        BenchLog.node = nodeId
    }

    private fun packet(id: String) = EmergencyRequest(
        id = id,
        type = Category.values().first(),
        latitude = 0.0,
        longitude = 0.0,
        timestamp = System.currentTimeMillis(),
        status = RequestStatus.CREATED,
        severity = 1,
        reporterId = nodeId
    )

    /** Returns true if the packet was a benchmark packet (caller must then ignore it). */
    fun handleIncoming(req: EmergencyRequest): Boolean {
        val id = req.id
        val key = id.substringAfter("-", "")
        when {
            id.startsWith("PING-") -> {
                BenchLog.log("PING_RECV", "key" to key)
                scope.launch {
                    BenchLog.log("PONG_SENT", "key" to key)
                    transport.broadcastRequest(packet("PONG-$key"))
                }
                return true
            }
            id.startsWith("PONG-") -> {
                BenchLog.log("PONG_RECV", "key" to key)
                return true
            }
            id.startsWith("LOSS-") -> {
                BenchLog.log("LOSS_RECV", "key" to key)
                return true
            }
        }
        return false
    }

    /** Round-trip test. Run on ONE phone; the other phone echoes automatically. */
    fun runPing(count: Int = 50, intervalMs: Long = 500) {
        scope.launch {
            val run = UUID.randomUUID().toString().take(4)
            BenchLog.log("TEST_START", "test" to "PING", "run" to run, "count" to count)
            for (n in 1..count) {
                BenchLog.log("PING_SENT", "key" to "$run-$n")
                transport.broadcastRequest(packet("PING-$run-$n"))
                delay(intervalMs)
            }
            delay(2_000)
            BenchLog.log("TEST_END", "test" to "PING", "run" to run)
        }
    }

    /** Packet-loss test. Run on ONE phone; the other phone logs what it receives. */
    fun runLoss(count: Int = 200, intervalMs: Long = 100) {
        scope.launch {
            val run = UUID.randomUUID().toString().take(4)
            BenchLog.log("TEST_START", "test" to "LOSS", "run" to run, "count" to count)
            for (n in 1..count) {
                BenchLog.log("LOSS_SENT", "key" to "$run-$n")
                transport.broadcastRequest(packet("LOSS-$run-$n"))
                delay(intervalMs)
            }
            delay(2_000)
            BenchLog.log("TEST_END", "test" to "LOSS", "run" to run)
        }
    }

    /** GPS error test. Keep the phone still (ideally outdoors). */
    fun runGps(count: Int = 100, intervalMs: Long = 1_000) {
        scope.launch {
            BenchLog.log("TEST_START", "test" to "GPS", "count" to count)
            for (n in 1..count) {
                val t0 = BenchLog.nowMs()
                val loc = locationProvider.getCurrentLocation()
                if (loc != null) {
                    BenchLog.log(
                        "GPS_FIX",
                        "n" to n,
                        "lat" to loc.latitude,
                        "lon" to loc.longitude,
                        "acc" to loc.accuracy,
                        "fix_ms" to String.format(Locale.US, "%.1f", BenchLog.nowMs() - t0)
                    )
                } else {
                    BenchLog.log("GPS_FAIL", "n" to n)
                }
                delay(intervalMs)
            }
            BenchLog.log("TEST_END", "test" to "GPS")
        }
    }
}