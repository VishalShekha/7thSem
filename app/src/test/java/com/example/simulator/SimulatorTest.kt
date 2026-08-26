package com.example.simulator

import com.example.core.Category
import com.example.core.EmergencyRequest
import com.example.core.InformationProcessingModule
import com.example.core.RequestStatus
import org.junit.Test
import java.util.UUID

class Node(val id: String) {
    val core = InformationProcessingModule()
    val naiveSeen = mutableSetOf<String>()
}

class SimulatorTest {

    @Test
    fun runSimulation() {
        val nodes = List(20) { Node("Node-$it") }
        
        var smartTransmissions = 0
        var naiveTransmissions = 0
        
        // Generate a set of events, including duplicates and resolved ones
        val events = mutableListOf<EmergencyRequest>()
        val baseLat = 34.0522
        val baseLon = -118.2437
        
        for (i in 1..50) {
            val isDuplicate = i % 3 == 0 // Every 3rd request is a fuzzy duplicate
            val lat = if (isDuplicate) baseLat + 0.0001 else baseLat + (i * 0.01)
            val status = if (i % 5 == 0) RequestStatus.RESOLVED else RequestStatus.CREATED
            
            events.add(
                EmergencyRequest(
                    id = UUID.randomUUID().toString(),
                    type = Category.WATER,
                    latitude = lat,
                    longitude = baseLon,
                    timestamp = System.currentTimeMillis() + (i * 1000),
                    status = status,
                    severity = (1..10).random(),
                    reporterId = "Node-${i % nodes.size}"
                )
            )
        }
        
        // Simulate Naive Flooding
        fun simulateNaive() {
            val queue = mutableListOf<Pair<String, EmergencyRequest>>()
            events.forEach { queue.add("Node-0" to it) }
            
            while(queue.isNotEmpty()) {
                val (sender, req) = queue.removeAt(0)
                for (node in nodes) {
                    if (node.id != sender) {
                        naiveTransmissions++
                        if (node.naiveSeen.add(req.id)) {
                            queue.add(node.id to req)
                        }
                    }
                }
            }
        }
        
        // Simulate Smart DTN
        fun simulateSmart() {
            val queue = mutableListOf<Pair<String, EmergencyRequest>>()
            events.forEach { queue.add("Node-0" to it) }
            
            while(queue.isNotEmpty()) {
                val (sender, req) = queue.removeAt(0)
                for (node in nodes) {
                    if (node.id != sender) {
                        smartTransmissions++
                        // We need a fresh copy to prevent reference mutation issues in simulation
                        val reqCopy = req.copy()
                        if (node.core.processIncomingRequest(reqCopy)) {
                            queue.add(node.id to reqCopy)
                        }
                    }
                }
            }
        }
        
        simulateNaive()
        simulateSmart()
        
        println("================ SIMULATION RESULTS ================")
        println("Naive Flooding Transmissions: $naiveTransmissions")
        println("Smart DTN Transmissions:      $smartTransmissions")
        
        val reduction = (1.0 - (smartTransmissions.toDouble() / naiveTransmissions.toDouble())) * 100
        println("Reduction in transmitted messages: ${"%.2f".format(reduction)}%")
        
        println("\nSample Area Summaries at Node-0:")
        nodes[0].core.getAreaSummaries().forEach { (cell, summary) ->
            println("  Grid $cell -> Severity: ${summary.totalSeverity}, Requests: ${summary.requestCounts}")
        }
        println("====================================================")
    }
}
