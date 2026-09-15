package com.prasoft.deskguard.radar

import android.media.ToneGenerator

enum class RadarStatus {
    NO_PERMISSIONS,
    DISCONNECTED,
    IDLE,
    PHONE_MOVING,
    CALIBRATING,
    ARMED_MONITORING,
    MOTION_DETECTED
}

enum class Sensitivity(val label: String, val thresholdDbm: Float) {
    LOW("Low (Large Motion)", 4.0f),
    MEDIUM("Medium (Walking)", 2.5f),
    HIGH("High (Subtle Presence)", 1.5f)
}

enum class RadarAudioTone(val displayName: String, val toneType: Int, val durationMs: Int) {
    TACTICAL_PING("Tactical Ping", ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 200),
    CYBER_BEEP("Cyber Beep", ToneGenerator.TONE_PROP_BEEP2, 180),
    SIREN_PULSE("Siren Pulse", ToneGenerator.TONE_SUP_INTERCEPT, 350),
    SOFT_CHIME("Soft Chime", ToneGenerator.TONE_PROP_PROMPT, 150)
}

data class DisturbanceEvent(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val deltaRssi: Float,
    val description: String,
    val photoPath: String? = null
)

data class WifiRadarUiState(
    val status: RadarStatus = RadarStatus.IDLE,
    val ssid: String = "Not Connected",
    val bssid: String = "",
    val frequencyMhz: Int = 0,
    val currentRssi: Int = -100,
    val baselineRssi: Float = 0f,
    val currentDelta: Float = 0f,
    val currentLatencyMs: Long = 0L,
    val calibrationProgress: Float = 0f,
    val isStationary: Boolean = true,
    val sensitivity: Sensitivity = Sensitivity.MEDIUM,
    val isSimulationMode: Boolean = false,
    val isSoundEnabled: Boolean = true,
    val selectedTone: RadarAudioTone = RadarAudioTone.TACTICAL_PING,
    val isFlashlightEnabled: Boolean = false,
    val isBackgroundGuardEnabled: Boolean = true,
    val isAdaptiveEmaEnabled: Boolean = true,
    val isIntruderCaptureEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val signalHistory: List<Float> = emptyList(),
    val eventLogs: List<DisturbanceEvent> = emptyList()
)
