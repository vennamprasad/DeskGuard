package com.prasoft.deskguard.radar

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Jetpack Process Lifecycle Observer that coordinates background/foreground
 * transitions for the Wi-Fi Radar Desk Guard system.
 */
class WifiRadarAppLifecycleObserver(
    private val engine: WifiRadarEngine
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        engine.onAppForegrounded()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        engine.onAppBackgrounded()
    }
}
