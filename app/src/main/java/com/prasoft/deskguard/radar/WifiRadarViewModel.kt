package com.prasoft.deskguard.radar

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.StateFlow

class WifiRadarViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val engine = WifiRadarEngine.getInstance(application)
    val uiState: StateFlow<WifiRadarUiState> = engine.uiState

    init {
        savedStateHandle.get<String>(KEY_SENSITIVITY)?.let { name ->
            try { engine.setSensitivity(Sensitivity.valueOf(name)) } catch (_: Exception) {}
        }
        savedStateHandle.get<String>(KEY_TONE)?.let { name ->
            try { engine.setAudioTone(RadarAudioTone.valueOf(name)) } catch (_: Exception) {}
        }
        savedStateHandle.get<Boolean>(KEY_SOUND)?.let { engine.toggleSound(it) }
        savedStateHandle.get<Boolean>(KEY_FLASHLIGHT)?.let { engine.toggleFlashlight(it) }
        savedStateHandle.get<Boolean>(KEY_BG_GUARD)?.let { engine.toggleBackgroundGuard(it) }
        savedStateHandle.get<Boolean>(KEY_INTRUDER_CAPTURE)?.let { engine.toggleIntruderCapture(it) }
    }

    fun startCalibrationAndArm() {
        engine.startCalibrationAndArm()
    }

    fun disarm() {
        engine.disarm()
    }

    fun setSensitivity(sensitivity: Sensitivity) {
        savedStateHandle[KEY_SENSITIVITY] = sensitivity.name
        engine.setSensitivity(sensitivity)
    }

    fun toggleSimulationMode(enabled: Boolean) {
        engine.toggleSimulationMode(enabled)
    }

    fun triggerSimulatedDisturbance() {
        engine.triggerSimulatedDisturbance()
    }

    fun toggleSound(enabled: Boolean) {
        savedStateHandle[KEY_SOUND] = enabled
        engine.toggleSound(enabled)
    }

    fun toggleFlashlight(enabled: Boolean) {
        savedStateHandle[KEY_FLASHLIGHT] = enabled
        engine.toggleFlashlight(enabled)
    }

    fun toggleBackgroundGuard(enabled: Boolean) {
        savedStateHandle[KEY_BG_GUARD] = enabled
        engine.toggleBackgroundGuard(enabled)
    }

    fun toggleAdaptiveEma(enabled: Boolean) {
        engine.toggleAdaptiveEma(enabled)
    }

    fun toggleIntruderCapture(enabled: Boolean) {
        savedStateHandle[KEY_INTRUDER_CAPTURE] = enabled
        engine.toggleIntruderCapture(enabled)
    }

    fun setAudioTone(tone: RadarAudioTone) {
        savedStateHandle[KEY_TONE] = tone.name
        engine.setAudioTone(tone)
    }

    fun previewTone(tone: RadarAudioTone) {
        engine.previewTone(tone)
    }

    fun toggleKeepScreenOn(enabled: Boolean) {
        engine.toggleKeepScreenOn(enabled)
    }

    fun clearLogs() {
        engine.clearLogs()
    }

    fun exportLogsToCsv(context: Context) {
        engine.exportLogsToCsv(context)
    }

    fun refreshNetworkInfo() {
        engine.refreshNetworkInfo()
    }

    override fun onCleared() {
        super.onCleared()
        val status = uiState.value.status
        val isArmed = status == RadarStatus.ARMED_MONITORING ||
                status == RadarStatus.CALIBRATING ||
                status == RadarStatus.MOTION_DETECTED
        if (!isArmed) {
            engine.destroy()
        }
    }

    companion object {
        private const val KEY_SENSITIVITY = "key_sensitivity"
        private const val KEY_TONE = "key_tone"
        private const val KEY_SOUND = "key_sound"
        private const val KEY_FLASHLIGHT = "key_flashlight"
        private const val KEY_BG_GUARD = "key_bg_guard"
        private const val KEY_INTRUDER_CAPTURE = "key_intruder_capture"
    }
}
