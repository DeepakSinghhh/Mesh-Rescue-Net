package com.example.offgridbridge

import androidx.lifecycle.ViewModel
import com.example.offgridbridge.utils.SecurityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MeshViewModel : ViewModel() {
    // A list of packets that the UI will observe
    private val _receivedPackets = MutableStateFlow<List<EmergencyPacket>>(emptyList())
    val receivedPackets: StateFlow<List<EmergencyPacket>> = _receivedPackets

    // For the UI to send a packet
    private val _outgoingPacket = MutableStateFlow<EmergencyPacket?>(null)
    val outgoingPacket: StateFlow<EmergencyPacket?> = _outgoingPacket

    private val _connectedPeersCount = MutableStateFlow(0)
    val connectedPeersCount: StateFlow<Int> = _connectedPeersCount

    val currentUserName = "User-${(100..999).random()}"

    fun addPacket(packet: EmergencyPacket) {
        val currentList = _receivedPackets.value.toMutableList()
        val existingPacketIndex = currentList.indexOfFirst { it.id == packet.id }

        if (existingPacketIndex != -1) {
            // Update existing packet
            currentList[existingPacketIndex] = packet
        } else {
            // Add new packet
            currentList.add(packet)
        }

        _receivedPackets.value = currentList.sortedBy { it.priority }
    }

    fun sendCustomSOS(messageText: String, isHighPriority: Boolean, targetId: String = "ALL", latitude: Double, longitude: Double) {
        val priority = if (isHighPriority) 0 else 2

        val messageWithLocation = "$messageText - GPS($latitude,$longitude)"
        val messageToSend = if (targetId != "ALL") {
            SecurityManager.encrypt(messageWithLocation, targetId)
        } else {
            messageWithLocation
        }

        val newPacket = EmergencyPacket(
            id = java.util.UUID.randomUUID().toString(),
            priority = priority,
            ttl = 5,
            message = messageToSend,
            senderId = currentUserName,
            senderPublicKey = SecurityManager.getMyPublicKey(),
            targetId = targetId
        )
        addPacket(newPacket)
        _outgoingPacket.value = newPacket
    }

    fun generateMockPacket(urgency: Int) {
        val mockPacket = EmergencyPacket(
            id = java.util.UUID.randomUUID().toString(),
            priority = urgency, // 0 for SOS, 2 for Info
            ttl = 5,
            message = if (urgency == 0) "MOCK: Critical Emergency!" else "MOCK: Status Update",
            senderId = "MockSender",
            senderPublicKey = "",
            targetId = "ALL"
        )
        // Add it to the list to see it on screen
        addPacket(mockPacket)
    }

    fun updatePeerCount(count: Int) {
        _connectedPeersCount.value = count
    }

    fun startMesh() {
        // This is a placeholder. The actual mesh starting logic is in the service.
    }
}