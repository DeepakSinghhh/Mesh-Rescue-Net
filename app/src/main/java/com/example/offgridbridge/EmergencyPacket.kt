package com.example.offgridbridge

data class EmergencyPacket(
    val id: String,                 // Unique Packet ID
    val senderId: String,           // My Phone Number/ID
    val senderPublicKey: String,    // My Public Key
    val targetId: String,           // "ALL" = Public. Specific Number = Private.
    val message: String,            // The content
    val priority: Int,              // 0 = SOS, 2 = Chat
    val ttl: Int = 5                // Time-To-Live (Hops)
)