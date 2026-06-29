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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.offgridbridge.ui.theme.*
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
                runOnUiThread { meshViewModel.addPacket(it) }
            }
            meshService?.setPeerCountListener {
                runOnUiThread { meshViewModel.updatePeerCount(it) }
            }
            meshService?.setUserName(meshViewModel.currentUserName)

            lifecycleScope.launch {
                meshViewModel.outgoingPacket.collect { packet ->
                    packet?.let {
                        meshService?.sendPacket(it)
                        meshViewModel.addPacket(it)
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
        com.example.offgridbridge.utils.SecurityManager.init(this)
        Log.d("Security", "My Public Key: ${com.example.offgridbridge.utils.SecurityManager.getMyPublicKey()}")

        Intent(this, MeshService::class.java).also { intent -> startService(intent) }

        setContent {
            OffGridBridgeTheme {
                MainScreen(meshViewModel,
                    onGatewayToggled = { meshService?.setIsGatewayNode(it) },
                    onStartMesh = { if (isBound) meshService?.startMesh() }
                )
            }
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
        if (isBound) { unbindService(serviceConnection); isBound = false }
    }
}

@SuppressLint("MissingPermission")
private fun getLastLocation(
    fusedLocationClient: FusedLocationProviderClient,
    onLocationFound: (Double, Double) -> Unit
) {
    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
        if (location != null) onLocationFound(location.latitude, location.longitude)
        else onLocationFound(26.4499, 80.3319)
    }
}

// ─── BRUTALIST COMPONENTS ────────────────────────────────────────────────────

@Composable
fun BrutalButton(
    text: String,
    onClick: () -> Unit,
    color: Color = BrutalYellow,
    textColor: Color = BrutalBlack,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(3.dp, BrutalWhite)
            .background(color)
            .padding(0.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = color),
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = text,
                color = textColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun BrutalCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, BrutalWhite)
            .background(BrutalGrayMid)
            .padding(12.dp),
        content = content
    )
}

@Composable
fun BrutalLabel(text: String, color: Color = BrutalGrayLight) {
    Text(
        text = text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 2.sp
    )
}

@Composable
fun BrutalTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BrutalLabel(label)
        Spacer(modifier = Modifier.height(4.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BrutalWhite),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = BrutalGrayMid,
                unfocusedContainerColor = BrutalGrayMid,
                focusedTextColor = BrutalWhite,
                unfocusedTextColor = BrutalWhite,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = BrutalYellow
            ),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            ),
            shape = RoundedCornerShape(0.dp)
        )
    }
}

// ─── MAIN SCREEN ─────────────────────────────────────────────────────────────

@Composable
fun MainScreen(
    viewModel: MeshViewModel,
    onGatewayToggled: (Boolean) -> Unit,
    onStartMesh: () -> Unit
) {
    val permissionsToRequest = remember {
        mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION).apply {
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
    var allPermissionsGranted by remember {
        mutableStateOf(permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions -> allPermissionsGranted = permissions.values.all { it } }

    val bluetoothAdapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    LaunchedEffect(allPermissionsGranted) {
        if (allPermissionsGranted) {
            onStartMesh()
            if (bluetoothAdapter?.isEnabled == false) {
                try { context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                catch (e: SecurityException) { Log.e("MainActivity", "BT enable failed", e) }
            }
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrutalBlack)
    ) {
        if (allPermissionsGranted) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var servicesEnabled by remember {
                mutableStateOf(
                    bluetoothAdapter?.isEnabled == true &&
                            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                )
            }

            if (servicesEnabled) {
                MeshDashboard(viewModel, onGatewayToggled, context)
            } else {
                ServicesDisabledScreen {
                    val isBtOn = bluetoothAdapter?.isEnabled == true
                    val isGpsOn = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    if (isBtOn && isGpsOn) servicesEnabled = true
                    else Toast.makeText(context, "ENABLE BT + GPS", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            PermissionScreen(
                onGrant = {
                    val granted = permissionsToRequest.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (granted) allPermissionsGranted = true
                    else permissionLauncher.launch(permissionsToRequest.toTypedArray())
                },
                onForce = {
                    allPermissionsGranted = true
                    Toast.makeText(context, "BYPASSING CHECKS...", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

// ─── DASHBOARD ───────────────────────────────────────────────────────────────

@Composable
fun MeshDashboard(
    viewModel: MeshViewModel,
    onGatewayToggled: (Boolean) -> Unit,
    context: Context
) {
    val connectedPeers by viewModel.connectedPeersCount.collectAsState()
    var isGateway by remember { mutableStateOf(false) }
    val packets by viewModel.receivedPackets.collectAsState()
    var messageText by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var targetId by remember { mutableStateOf("") }
    var isHighPriority by remember { mutableStateOf(false) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BrutalBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── HEADER ──
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "MESH//RESCUE",
                    color = BrutalYellow,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    letterSpacing = 4.sp
                )
                Text(
                    text = "OFF-GRID EMERGENCY NETWORK",
                    color = BrutalGrayLight,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 3.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = BrutalYellow, thickness = 3.dp)
            }
        }

        // ── STATUS BAR ──
        item {
            BrutalCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        BrutalLabel("NODE ID")
                        Text(
                            text = viewModel.currentUserName.take(10).uppercase(),
                            color = BrutalWhite,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BrutalLabel("PEERS")
                        Text(
                            text = "$connectedPeers",
                            color = if (connectedPeers > 0) BrutalGreen else BrutalRed,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        BrutalLabel("STATUS")
                        Text(
                            text = if (connectedPeers > 0) "LIVE" else "IDLE",
                            color = if (connectedPeers > 0) BrutalGreen else BrutalOrange,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }

        // ── GATEWAY TOGGLE ──
        item {
            BrutalCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        BrutalLabel("NODE MODE")
                        Text(
                            text = if (isGateway) "GATEWAY" else "STANDARD",
                            color = if (isGateway) BrutalYellow else BrutalWhite,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            letterSpacing = 2.sp
                        )
                    }
                    Switch(
                        checked = isGateway,
                        onCheckedChange = { isGateway = it; onGatewayToggled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BrutalBlack,
                            checkedTrackColor = BrutalYellow,
                            uncheckedThumbColor = BrutalGrayLight,
                            uncheckedTrackColor = BrutalGrayMid
                        )
                    )
                }
            }
        }

        // ── COMPOSE MESSAGE ──
        item {
            BrutalCard {
                BrutalLabel("COMPOSE MESSAGE")
                Spacer(modifier = Modifier.height(8.dp))
                BrutalTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = "MESSAGE CONTENT"
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Priority toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isHighPriority) "⚠ HIGH PRIORITY" else "LOW PRIORITY",
                        color = if (isHighPriority) BrutalRed else BrutalGrayLight,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Switch(
                        checked = isHighPriority,
                        onCheckedChange = { isHighPriority = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BrutalWhite,
                            checkedTrackColor = BrutalRed,
                            uncheckedThumbColor = BrutalGrayLight,
                            uncheckedTrackColor = BrutalGrayMid
                        )
                    )
                }

                // Private/Public toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isPrivate) "🔒 PRIVATE" else "📡 BROADCAST",
                        color = if (isPrivate) BrutalGreen else BrutalYellow,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BrutalBlack,
                            checkedTrackColor = BrutalGreen,
                            uncheckedThumbColor = BrutalGrayLight,
                            uncheckedTrackColor = BrutalGrayMid
                        )
                    )
                }

                if (isPrivate) {
                    Spacer(modifier = Modifier.height(8.dp))
                    BrutalTextField(
                        value = targetId,
                        onValueChange = { targetId = it },
                        label = "TARGET PUBLIC KEY"
                    )
                }
            }
        }

        // ── SOS BUTTON ──
        item {
            BrutalButton(
                text = "▶ SEND SOS + GPS",
                onClick = {
                    getLastLocation(LocationServices.getFusedLocationProviderClient(context)) { lat, long ->
                        viewModel.sendCustomSOS(
                            messageText = messageText,
                            isHighPriority = isHighPriority,
                            targetId = if (isPrivate) targetId else "ALL",
                            latitude = lat,
                            longitude = long
                        )
                        messageText = ""
                    }
                },
                color = BrutalYellow,
                textColor = BrutalBlack
            )
        }

        // ── TRIAGE TEST ──
        item {
            BrutalButton(
                text = "☠ TEST TRIAGE [FLOOD + SOS]",
                onClick = {
                    getLastLocation(LocationServices.getFusedLocationProviderClient(context)) { lat, long ->
                        repeat(30) {
                            viewModel.sendCustomSOS(
                                messageText = "Low Priority Spam $it",
                                isHighPriority = false,
                                latitude = lat,
                                longitude = long,
                                targetId = "ALL"
                            )
                        }
                        viewModel.sendCustomSOS(
                            messageText = "CRITICAL MEDICAL HELP",
                            isHighPriority = true,
                            latitude = lat,
                            longitude = long,
                            targetId = "ALL"
                        )
                    }
                },
                color = BrutalRed,
                textColor = BrutalWhite
            )
        }

        // ── PACKET LOG HEADER ──
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "// PACKET LOG",
                    color = BrutalYellow,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${packets.size} ENTRIES",
                    color = BrutalGrayLight,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            }
            HorizontalDivider(color = BrutalGrayMid, thickness = 1.dp)
        }

        // ── PACKETS ──
        if (packets.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BrutalGrayMid)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NO PACKETS RECEIVED",
                        color = BrutalGrayLight,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(packets) { packet ->
                val regex = "- GPS\\(([-+]?[0-9]*\\.?[0-9]+),([-+]?[0-9]*\\.?[0-9]+)\\)".toRegex()
                val matchResult = regex.find(packet.message)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 2.dp,
                            color = if (packet.message.contains("CRITICAL")) BrutalRed else BrutalGrayMid
                        )
                        .background(BrutalGrayMid)
                        .padding(12.dp)
                ) {
                    if (packet.message.contains("CRITICAL")) {
                        Text(
                            text = "⚠ CRITICAL",
                            color = BrutalRed,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = packet.message,
                        color = BrutalWhite,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                    if (matchResult != null) {
                        val (lat, long) = matchResult.destructured
                        Spacer(modifier = Modifier.height(8.dp))
                        BrutalButton(
                            text = "◉ VIEW ON MAP",
                            onClick = {
                                val uri = "geo:$lat,$long?q=$lat,$long(Victim)".toUri()
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            },
                            color = BrutalGreen,
                            textColor = BrutalBlack,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// ─── PERMISSION SCREEN ────────────────────────────────────────────────────────

@Composable
fun PermissionScreen(onGrant: () -> Unit, onForce: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrutalBlack)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MESH//\nRESCUE",
            color = BrutalYellow,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 48.sp,
            letterSpacing = 4.sp,
            lineHeight = 52.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = BrutalYellow, thickness = 3.dp)
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "PERMISSIONS REQUIRED\nTO OPERATE",
            color = BrutalWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "LOCATION + BLUETOOTH + NEARBY",
            color = BrutalGrayLight,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        BrutalButton(text = "▶ GRANT PERMISSIONS", onClick = onGrant, color = BrutalYellow, textColor = BrutalBlack)
        Spacer(modifier = Modifier.height(12.dp))
        BrutalButton(text = "⚠ DEMO: FORCE START", onClick = onForce, color = BrutalRed, textColor = BrutalWhite)
    }
}

// ─── SERVICES DISABLED SCREEN ─────────────────────────────────────────────────

@Composable
fun ServicesDisabledScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrutalBlack)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SIGNAL\nLOST",
            color = BrutalRed,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 56.sp,
            letterSpacing = 4.sp,
            lineHeight = 60.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = BrutalRed, thickness = 3.dp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "BLUETOOTH + GPS REQUIRED",
            color = BrutalWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "ENABLE BOTH SERVICES\nTHEN CONTINUE",
            color = BrutalGrayLight,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        BrutalButton(text = "▶ I HAVE ENABLED THEM", onClick = onRetry, color = BrutalYellow, textColor = BrutalBlack)
    }
}