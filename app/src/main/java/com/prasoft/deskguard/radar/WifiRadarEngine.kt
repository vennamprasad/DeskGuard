package com.prasoft.deskguard.radar

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

class WifiRadarEngine(context: Context) : SensorEventListener {

    companion object {
        @Volatile
        private var INSTANCE: WifiRadarEngine? = null

        fun getInstance(context: Context): WifiRadarEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WifiRadarEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val prefs = appContext.getSharedPreferences("wifi_radar_audit_logs", Context.MODE_PRIVATE)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var toneGenerator: ToneGenerator? = null

    private val _uiState = MutableStateFlow(WifiRadarUiState())
    val uiState: StateFlow<WifiRadarUiState> = _uiState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)
    private var samplingJob: Job? = null

    // Accelerometer smoothing
    private var lastAccMagnitude = 9.81f
    private var stationaryCounter = 0

    // Signal history buffer
    private val maxHistoryPoints = 60
    private val historyBuffer = ArrayDeque<Float>(maxHistoryPoints)

    // Baseline stats
    private var baselineMean = 0f
    private var baselineStdDev = 0f

    // Adaptive EMA learning rate for slow environmental drift
    private val emaAlpha = 0.003f

    // Simulation controls
    private var simulatedSpikeTicks = 0
    private var wasCarrierLost = false
    private val intruderCapturer = IntruderPhotoCapturer(appContext)

    // Camera flash ID
    private val flashCameraId: String? by lazy {
        try {
            cameraManager?.cameraIdList?.firstOrNull { id ->
                val chars = cameraManager?.getCameraCharacteristics(id)
                chars?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    private var isSensorRegistered = false

    init {
        registerSensors()
        loadPersistentLogs()
        refreshNetworkInfo()
    }

    fun registerSensors() {
        if (!isSensorRegistered) {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                isSensorRegistered = true
            }
        }
    }

    fun unregisterSensors() {
        if (isSensorRegistered) {
            sensorManager.unregisterListener(this)
            isSensorRegistered = false
        }
    }

    fun onAppForegrounded() {
        registerSensors()
        refreshNetworkInfo()
    }

    fun onAppBackgrounded() {
        val status = _uiState.value.status
        val isArmed = status == RadarStatus.ARMED_MONITORING ||
                status == RadarStatus.CALIBRATING ||
                status == RadarStatus.MOTION_DETECTED
        if (!isArmed) {
            unregisterSensors()
        }
    }

    fun toggleSimulationMode(enabled: Boolean) {
        _uiState.update { it.copy(isSimulationMode = enabled) }
        if (enabled) {
            _uiState.update {
                it.copy(
                    ssid = "Virtual_Lab_5G (Simulated)",
                    frequencyMhz = 5240,
                    currentRssi = -48,
                    status = if (it.status == RadarStatus.DISCONNECTED || it.status == RadarStatus.NO_PERMISSIONS) RadarStatus.IDLE else it.status
                )
            }
        } else {
            refreshNetworkInfo()
        }
    }

    fun toggleSound(enabled: Boolean) {
        _uiState.update { it.copy(isSoundEnabled = enabled) }
    }

    fun toggleFlashlight(enabled: Boolean) {
        _uiState.update { it.copy(isFlashlightEnabled = enabled) }
    }

    fun toggleBackgroundGuard(enabled: Boolean) {
        _uiState.update { it.copy(isBackgroundGuardEnabled = enabled) }
        val isArmed = _uiState.value.status == RadarStatus.ARMED_MONITORING ||
                _uiState.value.status == RadarStatus.CALIBRATING ||
                _uiState.value.status == RadarStatus.MOTION_DETECTED
        if (isArmed) {
            if (enabled) WifiRadarService.start(appContext) else WifiRadarService.stop(appContext)
        }
    }

    fun toggleAdaptiveEma(enabled: Boolean) {
        _uiState.update { it.copy(isAdaptiveEmaEnabled = enabled) }
    }

    fun toggleIntruderCapture(enabled: Boolean) {
        _uiState.update { it.copy(isIntruderCaptureEnabled = enabled) }
    }

    fun toggleKeepScreenOn(enabled: Boolean) {
        _uiState.update { it.copy(keepScreenOn = enabled) }
    }

    fun setAudioTone(tone: RadarAudioTone) {
        _uiState.update { it.copy(selectedTone = tone) }
    }

    fun previewTone(tone: RadarAudioTone) {
        _uiState.update { it.copy(selectedTone = tone) }
        playTone(tone)
    }

    fun triggerSimulatedDisturbance() {
        simulatedSpikeTicks = 4
    }

    fun startCalibrationAndArm() {
        if (!_uiState.value.isStationary) {
            _uiState.update { it.copy(status = RadarStatus.PHONE_MOVING) }
            return
        }

        if (_uiState.value.isBackgroundGuardEnabled) {
            WifiRadarService.start(appContext)
        }

        samplingJob?.cancel()
        samplingJob = scope.launch {
            _uiState.update {
                it.copy(
                    status = RadarStatus.CALIBRATING,
                    calibrationProgress = 0f
                )
            }

            val calibrationSamples = mutableListOf<Float>()
            val totalSteps = 20
            for (step in 1..totalSteps) {
                if (!isActive) return@launch
                val rssi = if (_uiState.value.isSimulationMode) {
                    (-48f + (Math.random().toFloat() * 0.4f - 0.2f)).toInt()
                } else {
                    val info = getCurrentWifiInfo()
                    info?.rssi ?: -100
                }
                calibrationSamples.add(rssi.toFloat())

                val progress = step.toFloat() / totalSteps
                _uiState.update {
                    it.copy(
                        calibrationProgress = progress,
                        currentRssi = rssi
                    )
                }
                delay(250L)
            }

            // Calculate baseline mean & std dev
            baselineMean = calibrationSamples.average().toFloat()
            val variance = calibrationSamples.map { (it - baselineMean) * (it - baselineMean) }.average().toFloat()
            baselineStdDev = sqrt(variance).coerceAtLeast(0.5f)

            _uiState.update {
                it.copy(
                    status = RadarStatus.ARMED_MONITORING,
                    baselineRssi = baselineMean,
                    calibrationProgress = 1f
                )
            }

            // Enter active monitoring loop
            monitorLoop()
        }
    }

    private suspend fun monitorLoop() {
        while (scope.isActive) {
            val isSim = _uiState.value.isSimulationMode
            val rssi: Int
            val latency: Long

            if (isSim) {
                val base = -48f
                val noise = (Math.random().toFloat() * 0.6f - 0.3f)
                if (simulatedSpikeTicks > 0) {
                    simulatedSpikeTicks--
                    val spikeDrop = when (_uiState.value.sensitivity) {
                        Sensitivity.LOW -> 5.2f
                        Sensitivity.MEDIUM -> 3.6f
                        Sensitivity.HIGH -> 2.4f
                    }
                    rssi = (base - spikeDrop + noise).toInt()
                    latency = 160L + (Math.random() * 40).toLong()
                } else {
                    rssi = (base + noise).toInt()
                    latency = 12L + (Math.random() * 6).toLong()
                }
            } else {
                val wifiInfo = getCurrentWifiInfo()
                if (wifiInfo == null || wifiInfo.rssi < -110) {
                    if (!wasCarrierLost) {
                        wasCarrierLost = true
                        recordEvent(0f, "RF Carrier Severed: Wi-Fi disconnected / power outage while armed")
                    }
                    _uiState.update { it.copy(status = RadarStatus.DISCONNECTED, currentDelta = 0f) }

                    // Hardware fallback: Physical tamper protection still active via accelerometer
                    if (!_uiState.value.isStationary) {
                        triggerAlert(6.0f)
                        recordEvent(6.0f, "Physical Tamper during outage: Phone moved while Wi-Fi disconnected")
                        delay(1200L.milliseconds)
                    } else {
                        delay(1000L.milliseconds)
                    }
                    continue
                } else if (wasCarrierLost) {
                    wasCarrierLost = false
                    val networkName = wifiInfo.ssid.ifBlank { "Office Network" }
                    recordEvent(0f, "RF Carrier Restored: Reconnected to $networkName, radar re-engaged")
                }
                rssi = wifiInfo.rssi
                latency = measureGatewayLatency()
            }

            val delta = abs(rssi - baselineMean)

            // Update waveform buffer
            synchronized(historyBuffer) {
                if (historyBuffer.size >= maxHistoryPoints) {
                    historyBuffer.removeFirst()
                }
                historyBuffer.add(rssi.toFloat())
            }

            val isStationary = _uiState.value.isStationary
            val currentSensitivity = _uiState.value.sensitivity
            val isMotionExceeded = delta >= currentSensitivity.thresholdDbm

            if (!isStationary) {
                triggerAlert(5.0f)
                recordEvent(5.0f, "Physical Tamper: Phone lifted or displaced from desk")
                _uiState.update {
                    it.copy(
                        status = RadarStatus.PHONE_MOVING,
                        currentRssi = rssi,
                        currentDelta = delta,
                        currentLatencyMs = latency,
                        signalHistory = historyBuffer.toList()
                    )
                }
                delay(1200L)
                continue
            } else if (isMotionExceeded) {
                triggerAlert(delta)
                _uiState.update {
                    it.copy(
                        status = RadarStatus.MOTION_DETECTED,
                        currentRssi = rssi,
                        currentDelta = delta,
                        currentLatencyMs = latency,
                        signalHistory = historyBuffer.toList()
                    )
                }
                delay(1500L) // Flash alert state briefly
                _uiState.update {
                    if (it.status == RadarStatus.MOTION_DETECTED) {
                        it.copy(status = RadarStatus.ARMED_MONITORING)
                    } else it
                }
            } else {
                // Adaptive EMA Drift compensation: slowly track long-term environmental shifts
                if (_uiState.value.isAdaptiveEmaEnabled && baselineMean != 0f) {
                    baselineMean = baselineMean * (1f - emaAlpha) + (rssi * emaAlpha)
                }

                _uiState.update {
                    it.copy(
                        status = RadarStatus.ARMED_MONITORING,
                        currentRssi = rssi,
                        baselineRssi = baselineMean,
                        currentDelta = delta,
                        currentLatencyMs = latency,
                        signalHistory = historyBuffer.toList()
                    )
                }
            }

            delay(250L)
        }
    }

    fun disarm() {
        samplingJob?.cancel()
        WifiRadarService.stop(appContext)
        _uiState.update { it.copy(status = RadarStatus.IDLE, currentDelta = 0f) }
    }

    fun setSensitivity(sensitivity: Sensitivity) {
        _uiState.update { it.copy(sensitivity = sensitivity) }
    }

    fun clearLogs() {
        prefs.edit().remove("saved_event_logs").apply()
        _uiState.update { it.copy(eventLogs = emptyList()) }
    }

    private fun triggerAlert(delta: Float) {
        // Haptic feedback
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 150), -1))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(250L)
            }
        }

        // Audible chime feedback if enabled
        if (_uiState.value.isSoundEnabled) {
            playTone(_uiState.value.selectedTone)
        }

        // Flashlight strobe if enabled
        if (_uiState.value.isFlashlightEnabled) {
            strobeFlashlight()
        }

        recordEvent(delta, "RF multipath spike: ${String.format(Locale.US, "%.1f", delta)} dBm deviation")
    }

    private fun recordEvent(delta: Float, description: String, photoPath: String? = null) {
        val event = DisturbanceEvent(
            deltaRssi = delta,
            description = description,
            photoPath = photoPath
        )
        val updated = listOf(event) + _uiState.value.eventLogs.take(50)
        _uiState.update { it.copy(eventLogs = updated) }
        savePersistentLogs(updated)

        // Capture intruder photo if enabled and delta is a significant motion/tamper
        if (photoPath == null && _uiState.value.isIntruderCaptureEnabled && delta >= 1.2f) {
            captureIntruderPhoto(event.id)
        }
    }

    private fun captureIntruderPhoto(targetEventId: Long) {
        intruderCapturer.capturePhoto { capturedPath ->
            _uiState.update { state ->
                val updatedLogs = state.eventLogs.map { ev ->
                    if (ev.id == targetEventId) ev.copy(photoPath = capturedPath) else ev
                }
                savePersistentLogs(updatedLogs)
                state.copy(eventLogs = updatedLogs)
            }
        }
    }

    private fun strobeFlashlight() {
        val camId = flashCameraId ?: return
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    repeat(3) {
                        cameraManager?.setTorchMode(camId, true)
                        delay(90L)
                        cameraManager?.setTorchMode(camId, false)
                        delay(90L)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun playTone(tone: RadarAudioTone) {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 75)
            }
            toneGenerator?.startTone(tone.toneType, tone.durationMs)
        } catch (_: Exception) {}
    }

    private suspend fun measureGatewayLatency(): Long = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val gatewayIp = getGatewayIp() ?: return@withContext 0L
            // Socket().use ensures the socket is unconditionally closed even on timeout or error
            Socket().use { socket ->
                socket.connect(InetSocketAddress(gatewayIp, 53), 200)
            }
            System.currentTimeMillis() - startTime
        } catch (_: Exception) {
            (System.currentTimeMillis() - startTime).coerceAtMost(250L)
        }
    }

    private fun getGatewayIp(): InetAddress? {
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val linkProps = connectivityManager.getLinkProperties(activeNetwork) ?: return null
        for (route in linkProps.routes) {
            if (route.isDefaultRoute && route.gateway != null) {
                return route.gateway
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun getCurrentWifiInfo(): WifiInfo? {
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (capabilities.transportInfo as? WifiInfo) ?: wifiManager.connectionInfo
        } else {
            wifiManager.connectionInfo
        }
    }

    fun refreshNetworkInfo() {
        if (_uiState.value.isSimulationMode) return
        val info = getCurrentWifiInfo()
        if (info != null && info.rssi > -110) {
            val rawSsid = info.ssid.replace("\"", "")
            val displaySsid = if (rawSsid.isEmpty() || rawSsid == "<unknown ssid>") "Connected Wi-Fi" else rawSsid
            _uiState.update {
                it.copy(
                    ssid = displaySsid,
                    bssid = info.bssid ?: "",
                    frequencyMhz = info.frequency,
                    currentRssi = info.rssi
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    ssid = "Not Connected",
                    status = RadarStatus.DISCONNECTED
                )
            }
        }
    }

    fun exportLogsToCsv(context: Context) {
        val logs = _uiState.value.eventLogs
        if (logs.isEmpty()) return

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val csvBuilder = StringBuilder("ID,Timestamp,Date_Time,SSID,Delta_dBm,Description\n")
        logs.forEach { log ->
            csvBuilder.append("${log.id},${log.timestamp},${dateFormat.format(Date(log.timestamp))},\"${_uiState.value.ssid}\",${log.deltaRssi},\"${log.description}\"\n")
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Wi-Fi Radar Audit Logs Export")
            putExtra(Intent.EXTRA_TEXT, csvBuilder.toString())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val chooser = Intent.createChooser(sendIntent, "Share Event History CSV").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(chooser)
    }

    private fun savePersistentLogs(events: List<DisturbanceEvent>) {
        val serialized = events.take(50).joinToString(";;") {
            "${it.id}|${it.timestamp}|${it.deltaRssi}|${it.description}|${it.photoPath ?: ""}"
        }
        prefs.edit().putString("saved_event_logs", serialized).apply()
    }

    private fun loadPersistentLogs() {
        val saved = prefs.getString("saved_event_logs", null) ?: return
        try {
            val list = saved.split(";;").mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size >= 4) {
                    DisturbanceEvent(
                        id = parts[0].toLongOrNull() ?: System.currentTimeMillis(),
                        timestamp = parts[1].toLongOrNull() ?: System.currentTimeMillis(),
                        deltaRssi = parts[2].toFloatOrNull() ?: 0f,
                        description = parts[3],
                        photoPath = if (parts.size >= 5 && parts[4].isNotBlank()) parts[4] else null
                    )
                } else null
            }
            _uiState.update { it.copy(eventLogs = list) }
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt(x * x + y * y + z * z)
            val deltaAcc = abs(magnitude - lastAccMagnitude)
            lastAccMagnitude = magnitude

            // If phone accelerates more than threshold, mark as in-motion
            if (deltaAcc > 0.35f || abs(magnitude - 9.81f) > 0.8f) {
                stationaryCounter = 0
                if (_uiState.value.isStationary) {
                    _uiState.update { it.copy(isStationary = false) }
                }
            } else {
                stationaryCounter++
                if (stationaryCounter > 8 && !_uiState.value.isStationary) {
                    _uiState.update { it.copy(isStationary = true) }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun destroy() {
        unregisterSensors()
        samplingJob?.cancel()
        WifiRadarService.stop(appContext)
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}
    }
}
