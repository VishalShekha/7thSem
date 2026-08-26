package com.example.core

import kotlin.math.*

enum class Category { WATER, FOOD, MEDICAL, SHELTER, OTHER }
enum class RequestStatus { CREATED, ACKNOWLEDGED, ASSIGNED, RESOLVED }

data class EmergencyRequest(
    val id: String,
    val type: Category,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    var status: RequestStatus,
    val severity: Int, // 1-10
    val reporterId: String
)

data class AreaSummary(
    val gridCell: String,
    var totalSeverity: Int,
    val requestCounts: MutableMap<Category, Int>
)

class InformationProcessingModule {
    private val requests = mutableMapOf<String, EmergencyRequest>()

    // 1. Duplicate detection: exact match + fuzzy/category/spatial-temporal match
    fun isDuplicateOrRedundant(newReq: EmergencyRequest): Boolean {
        // Exact match
        if (requests.containsKey(newReq.id)) return true

        // Fuzzy match: same category, within ~100m, within 1 hour
        for (existing in requests.values) {
            if (existing.type == newReq.type && existing.status != RequestStatus.RESOLVED) {
                val dist = haversine(newReq.latitude, newReq.longitude, existing.latitude, existing.longitude)
                val timeDiff = abs(newReq.timestamp - existing.timestamp)
                if (dist < 0.1 && timeDiff < 3600_000) { // 100 meters, 1 hour
                    return true
                }
            }
        }
        return false
    }

    // 4. Request lifecycle: Returns true if the request should be forwarded, false otherwise.
    fun processIncomingRequest(req: EmergencyRequest): Boolean {
        val existing = requests[req.id]

        if (existing != null) {
            // Update lifecycle if newer status
            if (req.status.ordinal > existing.status.ordinal) {
                existing.status = req.status
                // Resolved requests must stop propagating as new alarms, 
                // but we forward the resolution status once to update the network.
                return true
            }
            return false // Stale or exact duplicate status
        }

        // Drop fuzzy duplicates and resolved requests that we've never seen before
        // (no point propagating a resolved issue that we didn't know about)
        if (req.status == RequestStatus.RESOLVED) {
            requests[req.id] = req
            return false
        }

        if (isDuplicateOrRedundant(req)) {
            return false
        }

        requests[req.id] = req
        return true
    }

    // 2. Locality aggregation & 3. Area severity scoring
    fun getAreaSummaries(): Map<String, AreaSummary> {
        val summaries = mutableMapOf<String, AreaSummary>()
        for (req in requests.values) {
            if (req.status == RequestStatus.RESOLVED) continue

            val gridCell = getGridCell(req.latitude, req.longitude)
            val summary = summaries.getOrPut(gridCell) { AreaSummary(gridCell, 0, mutableMapOf()) }

            summary.totalSeverity += req.severity
            summary.requestCounts[req.type] = summary.requestCounts.getOrDefault(req.type, 0) + 1
        }
        return summaries
    }

    fun getAllRequests() = requests.values.toList()

    private fun getGridCell(lat: Double, lon: Double): String {
        // Simple grid cell: round to 2 decimal places (approx 1.1km box)
        val rLat = round(lat * 100) / 100
        val rLon = round(lon * 100) / 100
        return "$rLat,$rLon"
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Radius of the earth in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}
