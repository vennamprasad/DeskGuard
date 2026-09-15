package com.prasoft.deskguard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prasoft.deskguard.radar.DisturbanceEvent
import com.prasoft.deskguard.radar.RadarAudioTone
import com.prasoft.deskguard.radar.RadarStatus
import com.prasoft.deskguard.radar.Sensitivity
import com.prasoft.deskguard.radar.WifiRadarAppLifecycleObserver
import com.prasoft.deskguard.radar.WifiRadarViewModel
import com.prasoft.deskguard.ui.components.CircularRadarScanner
import com.prasoft.deskguard.ui.components.SignalOscilloscope
import com.prasoft.deskguard.ui.theme.CyberAmber
import com.prasoft.deskguard.ui.theme.CyberBackground
import com.prasoft.deskguard.ui.theme.CyberBorder
import com.prasoft.deskguard.ui.theme.CyberCrimson
import com.prasoft.deskguard.ui.theme.CyberCyan
import com.prasoft.deskguard.ui.theme.CyberEmerald
import com.prasoft.deskguard.ui.theme.CyberSurface
import com.prasoft.deskguard.ui.theme.CyberSurfaceElevated
import com.prasoft.deskguard.ui.theme.CyberTextMuted
import com.prasoft.deskguard.ui.theme.CyberTextPrimary
import com.prasoft.deskguard.ui.theme.CyberTextSecondary
import com.prasoft.deskguard.ui.theme.DeskGuardTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: WifiRadarViewModel by viewModels()
    private var lifecycleObserver: WifiRadarAppLifecycleObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register Jetpack ProcessLifecycleOwner to coordinate foreground/background transitions
        val observer = WifiRadarAppLifecycleObserver(viewModel.engine).also {
            lifecycleObserver = it
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)

        setContent {
            DeskGuardTheme(darkTheme = true) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = CyberBackground
                ) { innerPadding ->
                    WifiRadarDashboard(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        lifecycleObserver?.let {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
        }
        super.onDestroy()
    }
}

@Composable
fun WifiRadarDashboard(
    viewModel: WifiRadarViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var viewingPhotoPath by remember { mutableStateOf<String?>(null) }

    // Screen Keep-Awake handler when armed
    val isMonitoring = uiState.status == RadarStatus.ARMED_MONITORING ||
            uiState.status == RadarStatus.CALIBRATING ||
            uiState.status == RadarStatus.MOTION_DETECTED

    DisposableEffect(uiState.keepScreenOn, isMonitoring) {
        val window = (context as? Activity)?.window
        if (uiState.keepScreenOn && isMonitoring) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Permissions
    var hasPermissions by remember {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        mutableStateOf(
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        if (hasPermissions) {
            viewModel.refreshNetworkInfo()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            val needed = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBackground)
    ) {
        // High-Tech Header
        TacticalHeader(status = uiState.status)

        // Tactical Dual-Tab Selector
        TacticalTabBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            eventCount = uiState.eventLogs.size
        )

        // Tab Content
        if (selectedTab == 0) {
            // TAB 0: RADAR HUD VIEW
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                // RF Telemetry Hub Card
                item {
                    RfTelemetryCard(
                        ssid = uiState.ssid,
                        frequencyMhz = uiState.frequencyMhz,
                        rssi = uiState.currentRssi,
                        latencyMs = uiState.currentLatencyMs,
                        isStationary = uiState.isStationary
                    )
                }

                // Phosphor Radar Scanner
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularRadarScanner(
                            status = uiState.status,
                            currentRssi = uiState.currentRssi,
                            delta = uiState.currentDelta
                        )
                    }
                }

                // Calibration Progress Bar
                if (uiState.status == RadarStatus.CALIBRATING) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "CALIBRATING ROOM NOISE FLOOR... ${(uiState.calibrationProgress * 100).toInt()}%",
                                color = CyberCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { uiState.calibrationProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = CyberCyan,
                                trackColor = CyberSurface
                            )
                        }
                    }
                }

                // Oscilloscope Waveform
                item {
                    SignalOscilloscope(
                        signalHistory = uiState.signalHistory,
                        baseline = uiState.baselineRssi,
                        status = uiState.status
                    )
                }

                // Sensitivity Control
                item {
                    SensitivitySelector(
                        current = uiState.sensitivity,
                        onSelect = { viewModel.setSensitivity(it) }
                    )
                }

                // Action Controls (Arm / Disarm)
                item {
                    ActionControls(
                        status = uiState.status,
                        hasPermissions = hasPermissions,
                        isSimulationMode = uiState.isSimulationMode,
                        onRequestPermissions = {
                            val needed = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                                needed.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(needed.toTypedArray())
                        },
                        onArm = { viewModel.startCalibrationAndArm() },
                        onDisarm = { viewModel.disarm() }
                    )
                }

                // Simulator injector if simulation mode is active
                if (uiState.isSimulationMode) {
                    item {
                        SimulationControlCard(
                            isSimulationMode = uiState.isSimulationMode,
                            onToggle = { viewModel.toggleSimulationMode(it) },
                            isArmed = uiState.status == RadarStatus.ARMED_MONITORING || uiState.status == RadarStatus.MOTION_DETECTED,
                            onInjectDisturbance = { viewModel.triggerSimulatedDisturbance() }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        } else {
            // TAB 1: SYSTEM & AUDIT LOGS
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                // Alarm & Guard Preferences
                item {
                    AlertOptionsCard(
                        isSoundEnabled = uiState.isSoundEnabled,
                        onToggleSound = { viewModel.toggleSound(it) },
                        selectedTone = uiState.selectedTone,
                        onSelectTone = { viewModel.previewTone(it) },
                        isFlashlightEnabled = uiState.isFlashlightEnabled,
                        onToggleFlashlight = { viewModel.toggleFlashlight(it) },
                        isBackgroundGuardEnabled = uiState.isBackgroundGuardEnabled,
                        onToggleBackgroundGuard = { viewModel.toggleBackgroundGuard(it) },
                        isAdaptiveEmaEnabled = uiState.isAdaptiveEmaEnabled,
                        onToggleAdaptiveEma = { viewModel.toggleAdaptiveEma(it) },
                        isIntruderCaptureEnabled = uiState.isIntruderCaptureEnabled,
                        onToggleIntruderCapture = { viewModel.toggleIntruderCapture(it) },
                        keepScreenOn = uiState.keepScreenOn,
                        onToggleKeepScreenOn = { viewModel.toggleKeepScreenOn(it) }
                    )
                }

                // Simulation Mode Switchboard
                item {
                    SimulationControlCard(
                        isSimulationMode = uiState.isSimulationMode,
                        onToggle = { viewModel.toggleSimulationMode(it) },
                        isArmed = uiState.status == RadarStatus.ARMED_MONITORING || uiState.status == RadarStatus.MOTION_DETECTED,
                        onInjectDisturbance = { viewModel.triggerSimulatedDisturbance() }
                    )
                }

                // Event Logs Section
                item {
                    EventLogsSection(
                        events = uiState.eventLogs,
                        onClear = { viewModel.clearLogs() },
                        onExportCsv = { viewModel.exportLogsToCsv(context) },
                        onViewPhoto = { viewingPhotoPath = it }
                    )
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    viewingPhotoPath?.let { path ->
        IntruderPhotoDialog(
            photoPath = path,
            onDismiss = { viewingPhotoPath = null }
        )
    }
}

@Composable
fun TacticalHeader(status: RadarStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "DESKGUARD // WI-FI RADAR",
                color = CyberTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "Passive RF Presence & Multipath Guard",
                color = CyberTextSecondary,
                fontSize = 11.sp
            )
        }
        StatusBadge(status = status)
    }
}

@Composable
fun TacticalTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    eventCount: Int
) {
    val tabs = listOf("RADAR HUD", "SYSTEM & LOGS ($eventCount)")
    TabRow(
        selectedTabIndex = selectedTab,
        containerColor = CyberBackground,
        contentColor = CyberEmerald,
        indicator = { tabPositions ->
            SecondaryIndicator(
                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                color = CyberEmerald,
                height = 2.5.dp
            )
        },
        divider = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CyberBorder)
            )
        }
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = title,
                        color = if (selectedTab == index) CyberEmerald else CyberTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            )
        }
    }
}

@Composable
fun StatusBadge(status: RadarStatus) {
    val (color, text) = when (status) {
        RadarStatus.MOTION_DETECTED -> CyberCrimson to "ALERT"
        RadarStatus.CALIBRATING -> CyberCyan to "CALIBRATING"
        RadarStatus.ARMED_MONITORING -> CyberEmerald to "ARMED"
        RadarStatus.PHONE_MOVING -> CyberAmber to "PHONE MOVING"
        RadarStatus.DISCONNECTED -> CyberTextMuted to "NO WI-FI"
        else -> CyberTextSecondary to "STANDBY"
    }

    val animatedColor by animateColorAsState(targetValue = color, label = "BadgeColorAnim")

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CyberSurfaceElevated)
            .border(1.dp, animatedColor.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(animatedColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = animatedColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun RfTelemetryCard(
    ssid: String,
    frequencyMhz: Int,
    rssi: Int,
    latencyMs: Long,
    isStationary: Boolean
) {
    val bandText = when {
        frequencyMhz > 5900 -> "6 GHz (6E/7)"
        frequencyMhz > 4900 -> "5 GHz"
        frequencyMhz > 2400 -> "2.4 GHz"
        else -> "Wi-Fi"
    }

    val (advisoryTitle, advisoryText, advisoryColor) = when {
        frequencyMhz > 5900 -> Triple(
            "6 GHz WI-FI SENSING ACTIVE",
            "Ultra-fine ~5 cm subcarrier density. Maximum sensitivity to presence & micro-movement.",
            CyberEmerald
        )
        frequencyMhz > 4900 -> Triple(
            "5 GHz OPTIMAL SENSING BAND",
            "Optimal ~6 cm wavelength. High multipath resolution for human presence detection.",
            CyberEmerald
        )
        frequencyMhz > 2400 -> Triple(
            "2.4 GHz WIDE COVERAGE BAND",
            "Broad room penetration. Tip: Connect to your 5 GHz band for 3x higher motion sensitivity.",
            CyberAmber
        )
        else -> Triple(
            "NO ACTIVE WI-FI",
            "Connect to a Wi-Fi router or toggle Emulator Simulation Mode to start sensing.",
            CyberTextMuted
        )
    }

    // Normalized RSSI (0.0 to 1.0) between -95 dBm and -30 dBm
    val normalizedRssi = ((rssi.coerceIn(-95, -30) + 95) / 65f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CyberSurface)
            .border(1.dp, CyberBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ssid,
                    color = CyberTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyberCyan.copy(alpha = 0.15f))
                        .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = bandText,
                        color = CyberCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Signal bar meter
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "SIGNAL LEVEL", color = CyberTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "$rssi dBm", color = CyberEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                LinearProgressIndicator(
                    progress = { normalizedRssi },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = CyberEmerald,
                    trackColor = CyberBackground
                )
            }

            // Telemetry Columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(label = "GATEWAY PING", value = "${latencyMs}ms", color = CyberCyan)
                MetricItem(
                    label = "SURFACE",
                    value = if (isStationary) "STATIONARY" else "IN MOTION",
                    color = if (isStationary) CyberEmerald else CyberAmber
                )
            }

            // Advisory Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyberBackground.copy(alpha = 0.6f))
                    .border(1.dp, advisoryColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = advisoryTitle,
                        color = advisoryColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = advisoryText,
                        color = CyberTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String, color: Color) {
    Column {
        Text(text = label, color = CyberTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun SensitivitySelector(
    current: Sensitivity,
    onSelect: (Sensitivity) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "TRIGGER SENSITIVITY",
            color = CyberTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Sensitivity.entries.forEach { sensitivity ->
                val isSelected = current == sensitivity
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(sensitivity) },
                    label = {
                        Text(
                            text = sensitivity.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberEmerald.copy(alpha = 0.2f),
                        selectedLabelColor = CyberEmerald,
                        containerColor = CyberSurface,
                        labelColor = CyberTextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = CyberBorder,
                        selectedBorderColor = CyberEmerald,
                        enabled = true,
                        selected = isSelected
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ActionControls(
    status: RadarStatus,
    hasPermissions: Boolean,
    isSimulationMode: Boolean,
    onRequestPermissions: () -> Unit,
    onArm: () -> Unit,
    onDisarm: () -> Unit
) {
    if (!hasPermissions && !isSimulationMode) {
        Button(
            onClick = onRequestPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "GRANT WI-FI PERMISSIONS",
                color = CyberBackground,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
        return
    }

    val isArmed = status == RadarStatus.ARMED_MONITORING ||
            status == RadarStatus.CALIBRATING ||
            status == RadarStatus.MOTION_DETECTED

    val infiniteTransition = rememberInfiniteTransition(label = "ArmButtonPulse")
    val borderGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAnim"
    )

    if (isArmed) {
        Button(
            onClick = onDisarm,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .border(1.5.dp, CyberCrimson.copy(alpha = borderGlow), RoundedCornerShape(10.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCrimson),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "DISARM RADAR",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    } else {
        Button(
            onClick = onArm,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "CALIBRATE & ARM GUARD",
                color = CyberBackground,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun AlertOptionsCard(
    isSoundEnabled: Boolean,
    onToggleSound: (Boolean) -> Unit,
    selectedTone: RadarAudioTone,
    onSelectTone: (RadarAudioTone) -> Unit,
    isFlashlightEnabled: Boolean,
    onToggleFlashlight: (Boolean) -> Unit,
    isBackgroundGuardEnabled: Boolean,
    onToggleBackgroundGuard: (Boolean) -> Unit,
    isAdaptiveEmaEnabled: Boolean,
    onToggleAdaptiveEma: (Boolean) -> Unit,
    isIntruderCaptureEnabled: Boolean,
    onToggleIntruderCapture: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CyberSurface)
            .border(1.dp, CyberBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "ALARM & MONITORING PREFERENCES",
                color = CyberTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            // Audio Alert
            PreferenceSwitchRow(
                title = "Audible Alarm",
                subtitle = "Sounds radar chime on presence spike",
                checked = isSoundEnabled,
                onCheckedChange = onToggleSound
            )

            // Tone Selector
            if (isSoundEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ALARM TONE (TAP TO PREVIEW)",
                        color = CyberTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RadarAudioTone.entries.forEach { tone ->
                            val isSelected = tone == selectedTone
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectTone(tone) },
                                label = {
                                    Text(
                                        text = tone.displayName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyberCyan.copy(alpha = 0.25f),
                                    selectedLabelColor = CyberCyan,
                                    containerColor = CyberBackground,
                                    labelColor = CyberTextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = CyberBorder,
                                    selectedBorderColor = CyberCyan,
                                    enabled = true,
                                    selected = isSelected
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Flashlight Strobe
            PreferenceSwitchRow(
                title = "Flashlight Strobe",
                subtitle = "Flashes LED torch on intrusion in dark rooms",
                checked = isFlashlightEnabled,
                onCheckedChange = onToggleFlashlight
            )

            // Front-Camera Intruder Photo Snap
            PreferenceSwitchRow(
                title = "Front-Camera Intruder Snap",
                subtitle = "Silently captures evidence photo on motion or tamper",
                checked = isIntruderCaptureEnabled,
                onCheckedChange = onToggleIntruderCapture
            )

            // Background Guard
            PreferenceSwitchRow(
                title = "Screen-Off Background Guard",
                subtitle = "Runs Foreground Service to guard when phone locked",
                checked = isBackgroundGuardEnabled,
                onCheckedChange = onToggleBackgroundGuard
            )

            // Adaptive EMA Drift
            PreferenceSwitchRow(
                title = "Adaptive Drift Compensation",
                subtitle = "Filters slow temperature/router shifts (prevents false alarms)",
                checked = isAdaptiveEmaEnabled,
                onCheckedChange = onToggleAdaptiveEma
            )

            // Keep Screen Awake
            PreferenceSwitchRow(
                title = "Keep Screen Awake",
                subtitle = "Prevents screen sleeping while armed on desk",
                checked = keepScreenOn,
                onCheckedChange = onToggleKeepScreenOn
            )
        }
    }
}

@Composable
fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = CyberTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = CyberTextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberBackground,
                checkedTrackColor = CyberEmerald,
                uncheckedThumbColor = CyberTextSecondary,
                uncheckedTrackColor = CyberBackground
            )
        )
    }
}

@Composable
fun SimulationControlCard(
    isSimulationMode: Boolean,
    onToggle: (Boolean) -> Unit,
    isArmed: Boolean,
    onInjectDisturbance: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CyberSurface)
            .border(
                1.dp,
                if (isSimulationMode) CyberCyan.copy(alpha = 0.6f) else CyberBorder,
                RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EMULATOR / LAB SIMULATION",
                        color = if (isSimulationMode) CyberCyan else CyberTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Generates synthetic RF signal for testing without physical motion",
                        color = CyberTextSecondary,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = isSimulationMode,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyberBackground,
                        checkedTrackColor = CyberCyan,
                        uncheckedThumbColor = CyberTextSecondary,
                        uncheckedTrackColor = CyberBackground
                    )
                )
            }

            if (isSimulationMode) {
                if (isArmed) {
                    Button(
                        onClick = onInjectDisturbance,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCrimson),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "⚡ INJECT SIMULATED DISTURBANCE",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Text(
                        text = "Tap 'CALIBRATE & ARM GUARD' on the Radar HUD tab, then inject disturbance.",
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun EventLogsSection(
    events: List<DisturbanceEvent>,
    onClear: () -> Unit,
    onExportCsv: () -> Unit,
    onViewPhoto: (String) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CyberSurface)
            .border(1.dp, CyberBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AUDIT LOG (${events.size})",
                    color = CyberTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (events.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onExportCsv,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(text = "EXPORT CSV", fontSize = 10.sp, color = CyberCyan, fontFamily = FontFamily.Monospace)
                        }
                        OutlinedButton(
                            onClick = onClear,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(text = "CLEAR", fontSize = 10.sp, color = CyberTextSecondary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            if (events.isEmpty()) {
                Text(
                    text = "No disturbance events recorded. Place phone on flat desk and arm guard.",
                    color = CyberTextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                events.forEach { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.description,
                                color = CyberCrimson,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = timeFormat.format(Date(event.timestamp)),
                                color = CyberTextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!event.photoPath.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CyberCyan.copy(alpha = 0.15f))
                                        .border(1.dp, CyberCyan, RoundedCornerShape(6.dp))
                                        .clickable { onViewPhoto(event.photoPath) }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(text = "📷", fontSize = 10.sp)
                                        Text(
                                            text = "PHOTO",
                                            color = CyberCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "+${String.format(Locale.US, "%.1f", event.deltaRssi)} dBm",
                                color = CyberCrimson,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IntruderPhotoDialog(
    photoPath: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(photoPath) {
        try {
            BitmapFactory.decodeFile(photoPath)
        } catch (_: Exception) {
            null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CyberSurfaceElevated)
                .border(1.dp, CyberCyan, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INTRUDER EVIDENCE",
                        color = CyberCrimson,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "FRONT CAM",
                        color = CyberCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured Intruder Evidence",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, CyberBorder, RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Photo file not found",
                            color = CyberTextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Text(
                    text = photoPath.substringAfterLast('/'),
                    color = CyberTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "CLOSE", color = CyberTextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Desk Guard Intruder Evidence")
                                putExtra(Intent.EXTRA_TEXT, "Desk Guard captured an intruder at the desk. File: $photoPath")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Intruder Alert"))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "SHARE", color = CyberCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}