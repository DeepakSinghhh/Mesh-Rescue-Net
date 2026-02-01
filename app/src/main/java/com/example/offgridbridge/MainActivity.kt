package com.example.offgridbridge

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.offgridbridge.utils.SecurityManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val meshViewModel: MeshViewModel by viewModels()
    private var meshService: MeshService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MeshService.MeshBinder
            meshService = binder.getService()
            isBound = true

            meshService?.setPacketListener {
                runOnUiThread {
                    meshViewModel.addPacket(it)
                }
            }

            meshService?.setPeerCountListener {
                runOnUiThread {
                    meshViewModel.updatePeerCount(it)
                }
            }

            meshService?.setUserName(meshViewModel.currentUserName)

            // Observe outgoing packets from the ViewModel
            lifecycleScope.launch {
                meshViewModel.outgoingPacket.collect { packet ->
                    packet?.let {
                        meshService?.sendPacket(it)
                        meshViewModel.addPacket(it) // Also add to local UI immediately
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            meshService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start the Math Engine
        SecurityManager.init(this)
        Log.d("Security", "My Public Key: ${SecurityManager.getMyPublicKey()}")

        // Start the MeshService
        Intent(this, MeshService::class.java).also { intent ->
            startService(intent)
        }

        setContent {
            MainScreen(meshViewModel, onGatewayToggled = {
                meshService?.setIsGatewayNode(it)
            }, onStartMesh = {
                if (isBound) {
                    meshService?.startMesh()
                }
            })
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, MeshService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}

@SuppressLint("MissingPermission")
private fun getLastLocation(
    fusedLocationClient: FusedLocationProviderClient,
    onLocationFound: (Double, Double) -> Unit
) {
    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            onLocationFound(location.latitude, location.longitude)
        } else {
            // Default to a dummy location if GPS fails (common indoors)
            onLocationFound(26.4499, 80.3319) // Kanpur coordinates
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MeshViewModel,
    onGatewayToggled: (Boolean) -> Unit,
    onStartMesh: () -> Unit
) {
    val permissionsToRequest = remember {
        mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val context = LocalContext.current
    var allPermissionsGranted by remember { mutableStateOf(permissionsToRequest.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        allPermissionsGranted = permissions.values.all { it }
    }

    val bluetoothAdapter = remember {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter
    }

    fun checkAndEnableBluetooth(context: Context) {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            try {
                context.startActivity(enableBtIntent)
            } catch (e: SecurityException) {
                Log.e("MainActivity", "Failed to request Bluetooth enable.", e)
            }
        }
    }

    LaunchedEffect(allPermissionsGranted) {
        if (allPermissionsGranted) {
            onStartMesh()
            checkAndEnableBluetooth(context)
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    if (allPermissionsGranted) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var servicesEnabled by remember { mutableStateOf(bluetoothAdapter?.isEnabled == true && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) }

        if (servicesEnabled) {
            val connectedPeers by viewModel.connectedPeersCount.collectAsState()
            var isGateway by remember { mutableStateOf(false) }
            val packets by viewModel.receivedPackets.collectAsState()
            var messageText by remember { mutableStateOf("") }
            var isPrivate by remember { mutableStateOf(false) }
            var targetId by remember { mutableStateOf("") }
            var isHighPriority by remember { mutableStateOf(false) }
            val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Username: ${viewModel.currentUserName}")
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Connected: $connectedPeers")
                }

                Text("My Public Key: ${SecurityManager.getMyPublicKey()}")

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Gateway Node")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = isGateway,
                        onCheckedChange = {
                            isGateway = it
                            onGatewayToggled(it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Enter Emergency Message") }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("High Priority")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = isHighPriority, onCheckedChange = { isHighPriority = it })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mode: ")
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                    Text(if (isPrivate) " Private (Secure)" else " Public (Broadcast)")
                }

                if (isPrivate) {
                    TextField(
                        value = targetId,
                        onValueChange = { targetId = it },
                        label = { Text("Enter Target Public Key") },
                        placeholder = { Text("e.g. 123abc...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(onClick = {
                    getLastLocation(fusedLocationClient) { lat, long ->
                        viewModel.sendCustomSOS(
                            messageText = messageText,
                            isHighPriority = isHighPriority,
                            targetId = if (isPrivate) targetId else "ALL",
                            latitude = lat,
                            longitude = long
                        )
                        messageText = "" // Clear input
                    }
                }) {
                    Text("Send SOS + GPS")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        getLastLocation(fusedLocationClient) { lat, long ->
                            // 1. Flood the queue with 30 "Junk" messages (Low Priority)
                            repeat(30) {
                                viewModel.sendCustomSOS(
                                    messageText = "Low Priority Spam $it",
                                    isHighPriority = false,
                                    latitude = lat,
                                    longitude = long,
                                    targetId = "ALL"
                                )
                            }

                            // 2. Immediately send 1 "Critical SOS" (High Priority)
                            viewModel.sendCustomSOS(
                                messageText = "CRITICAL MEDICAL HELP",
                                isHighPriority = true,
                                latitude = lat,
                                longitude = long,
                                targetId = "ALL"
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("TEST TRIAGE (Flood + SOS)")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Packet History", style = MaterialTheme.typography.headlineSmall)
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(packets) { packet ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Message: ${packet.message}")

                                // Find GPS coordinates in the message
                                val regex = "- GPS\\(([-+]?[0-9]*\\.?[0-9]+),([-+]?[0-9]*\\.?[0-9]+)\\)".toRegex()
                                val matchResult = regex.find(packet.message)

                                if (matchResult != null) {
                                    val (lat, long) = matchResult.destructured
                                    Button(onClick = {
                                        val uri = "geo:$lat,$long?q=$lat,$long(Victim)".toUri()
                                        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                                        context.startActivity(mapIntent)
                                    }) {
                                        Text("View on Map 🗺️")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Please enable Bluetooth and Location services.")
                    Button(onClick = {
                        val isBtOn = bluetoothAdapter?.isEnabled == true
                        val isGpsOn = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

                        if (isBtOn && isGpsOn) {
                            servicesEnabled = true
                        } else {
                            Toast.makeText(context, "Please turn on Bluetooth & Location", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("I Have Turned Them On")
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("App requires location and Bluetooth permissions to function.")

                Spacer(modifier = Modifier.height(16.dp))

                // The Original Button (Keep this)
                Button(onClick = {
                    val permissionsAreNowGranted = permissionsToRequest.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (permissionsAreNowGranted) {
                        allPermissionsGranted = true
                    } else {
                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    }
                }) {
                    Text("Grant Permissions")
                }

                Spacer(modifier = Modifier.height(24.dp))

                // *** ADD THIS NEW BUTTON ***
                OutlinedButton(
                    onClick = {
                        // FORCE the dialog to close so you can see the app
                        allPermissionsGranted = true

                        // Optional: Show a toast so you know it was forced
                        Toast.makeText(context, "Bypassing Checks...", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                ) {
                    Text("⚠️ DEMO: FORCE START APP")
                }
            }
        }
    }
}
