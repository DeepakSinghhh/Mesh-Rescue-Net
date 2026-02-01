package com.example.offgridbridge

import java.util.concurrent.ConcurrentLinkedQueue

class TriageManager {

    // Threshold: If queue has more than 20 packets, we consider it "Congested"
    private val CONGESTION_THRESHOLD = 20

    // The actual buffer of packets waiting to be sent
    private val transmissionQueue = ConcurrentLinkedQueue<EmergencyPacket>()

    // 1. ADD PACKET (The "Gatekeeper")
    fun addToQueue(packet: EmergencyPacket) {
        // Step A: Check for Congestion
        if (transmissionQueue.size >= CONGESTION_THRESHOLD) {
            runTriageProtocol()
        }

        // Step B: Add the new packet
        transmissionQueue.add(packet)
    }

    // 2. THE ALGORITHM (The "Killer")
    private fun runTriageProtocol() {
        println("TRIAGE ALERT: Network Congested! Dropping low priority packets...")

        // REMOVE any packet that is NOT Critical (Priority > 0)
        // This instantly clears space for SOS messages.
        transmissionQueue.removeIf { it.priority > 0 }
    }

    // 3. GET NEXT PACKET (The "Sorter")
    fun getNextPacketToSend(): EmergencyPacket? {
        // Always pick the one with the Highest Priority (Lowest number)
        val bestPacket = transmissionQueue.minByOrNull { it.priority }

        if (bestPacket != null) {
            transmissionQueue.remove(bestPacket)
        }
        return bestPacket
    }
}