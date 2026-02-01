package com.example.offgridbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.offgridbridge.utils.SecurityManager
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import android.os.Handler

class MeshService : Service() {

    inner class MeshBinder : Binder() {
        fun getService(): MeshService = this@MeshService
    }

    private val binder = MeshBinder()
    private var packetListener: ((EmergencyPacket) -> Unit)? = null
    private var peerCountListener: ((Int) -> Unit)? = null

    private lateinit var connectionsClient: ConnectionsClient
    private val gson = Gson()
    private val triageManager = TriageManager()
    private val discoveredEndpoints = mutableMapOf<String, String>() // Endpoint ID -> Endpoint Name
    private val establishedConnections = mutableSetOf<String>() // Endpoint IDs of connected devices
    private val processedPacketIds = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private val serviceId = "com.example.offgridbridge"
    private var currentUserName = "User-${(100..999).random()}"

    var isGatewayNode = false // Determines if this device is a gateway

    //region Lifecycle Callbacks
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Device found: ${info.endpointName}. Accepting connection...")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.d(TAG, "CONNECTED to $endpointId")
                establishedConnections.add(endpointId)
                peerCountListener?.invoke(establishedConnections.size)
                processQueue() // Kick off queue processing for the new connection
            } else {
                Log.e(TAG, "CONNECTION FAILED: ${result.status.statusMessage}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            establishedConnections.remove(endpointId)
            peerCountListener?.invoke(establishedConnections.size)
        }
    }
    //endregion

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, discoveredEndpointInfo: DiscoveredEndpointInfo) {
            Log.i(TAG, "onEndpointFound: endpoint found, connecting to ${discoveredEndpointInfo.endpointName}")
            connectionsClient.requestConnection(
                currentUserName,
                endpointId,
                connectionLifecycleCallback
            ).addOnSuccessListener {
                Log.i(TAG, "requestConnection: success to $endpointId")
            }.addOnFailureListener { e ->
                Log.e(TAG, "requestConnection: failure to $endpointId", e)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.i(TAG, "onEndpointLost: endpoint lost $endpointId")
            discoveredEndpoints.remove(endpointId)
        }
    }

    //region Payload Callback
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return

            try {
                val jsonStr = String(payload.asBytes()!!)
                var packet = gson.fromJson(jsonStr, EmergencyPacket::class.java)

                // 1. DEDUPLICATION: Ignore if we've seen this Packet ID before
                if (processedPacketIds.contains(packet.id)) return
                processedPacketIds.add(packet.id)

                // 2. DECRYPT IF PRIVATE
                if (packet.targetId != "ALL" && packet.targetId == currentUserName) {
                    val decryptedMessage = SecurityManager.decrypt(packet.message, packet.senderPublicKey)
                    packet = packet.copy(message = decryptedMessage)
                }

                // 3. SHOW IT (If it's Public or for Me)
                val isForMe = (packet.targetId == "ALL") || (packet.targetId == currentUserName)
                if (isForMe) {
                    packetListener?.invoke(packet)
                }

                // 4. THE BRIDGE: Relay to everyone else
                if (packet.ttl > 0) {
                    val newTtl = packet.ttl - 1
                    val relayPacket = packet.copy(ttl = newTtl)

                    Log.d("MeshService", "Relaying packet from $endpointId to others...")
                    relayToNeighbors(relayPacket, excludeEndpointId = endpointId)
                }

            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "Failed to deserialize packet from $endpointId", e)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }
    //endregion

    override fun onCreate() {
        super.onCreate()
        connectionsClient = Nearby.getConnectionsClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(1, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionsClient.stopAllEndpoints()
    }

    fun setPacketListener(listener: (EmergencyPacket) -> Unit) {
        packetListener = listener
    }

    fun setPeerCountListener(listener: (Int) -> Unit) {
        peerCountListener = listener
    }

    fun sendPacket(packet: EmergencyPacket, endpoints: Set<String> = establishedConnections) {
        triageManager.addToQueue(packet)
        processQueue()
    }

    private fun relayToNeighbors(packet: EmergencyPacket, excludeEndpointId: String? = null) {
        if (establishedConnections.isEmpty()) return

        // Filter: Send to everyone EXCEPT the sender (excludeEndpointId)
        val targets = establishedConnections.filter { it != excludeEndpointId }

        if (targets.isNotEmpty()) {
            val jsonStr = gson.toJson(packet)
            val payload = Payload.fromBytes(jsonStr.toByteArray())

            connectionsClient.sendPayload(targets, payload)
                .addOnSuccessListener { Log.d(TAG, "Relayed packet to ${targets.size} neighbors") }
                .addOnFailureListener { e -> Log.e(TAG, "Relay Failed: ${e.message}") }
        }
    }

    private fun processQueue() {
        if (establishedConnections.isEmpty()) {
            Log.w(TAG, "No devices connected. Packet remains in queue.")
            return
        }

        val packetToSend = triageManager.getNextPacketToSend() ?: return // Get next packet

        val payload = Payload.fromBytes(gson.toJson(packetToSend).toByteArray())

        connectionsClient.sendPayload(ArrayList(establishedConnections), payload)
            .addOnSuccessListener {
                Log.d(TAG, "Message sent successfully! Processing next in queue...")
                processQueue() // Recursively process
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send: ${e.message}. Packet lost. Processing next in queue.")
                processQueue() // Process next packet even if this one fails
            }
    }

    private fun logToRescueServer(receivedPacket: EmergencyPacket) {
        // 1. Mark as Delivered (Conceptually)
        // Since we removed the status field, we will just log it here

        // 2. Update UI locally to show success
        packetListener?.invoke(receivedPacket)

        // 3. STOP here. Do not relay to neighbors.
        Log.i(TAG, "GATEWAY: Packet rescued: $receivedPacket")
        return
    }

    fun startMesh() {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

        // 1. Start Advertising
        connectionsClient.startAdvertising(
            currentUserName, serviceId, connectionLifecycleCallback, advertisingOptions
        ).addOnFailureListener { Log.e(TAG, "Advertising Failed: ${it.message}") }

        // 2. Start Discovery (Wait 2 seconds so Advertising starts first)
        Handler(Looper.getMainLooper()).postDelayed({
            val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

            connectionsClient.startDiscovery(
                serviceId, // MUST MATCH Advertising Service ID exactly
                endpointDiscoveryCallback,
                discoveryOptions
            ).addOnFailureListener { Log.e(TAG, "Discovery Failed: ${it.message}") }
        }, 2000)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "MESH_SERVICE_CHANNEL",
                "Mesh Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "MESH_SERVICE_CHANNEL")
            .setContentTitle("Off-Grid Bridge")
            .setContentText("Running mesh network service...")
            .build()
    }

    private fun sendNotification(message: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, "MESH_SERVICE_CHANNEL")
            .setContentTitle("Private Message")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your app's icon
            .build()
        notificationManager.notify(2, notification) // Use a different ID for private messages
    }

    fun setUserName(name: String) {
        this.currentUserName = name
    }

    fun setIsGatewayNode(isGateway: Boolean) {
        this.isGatewayNode = isGateway
    }
}
