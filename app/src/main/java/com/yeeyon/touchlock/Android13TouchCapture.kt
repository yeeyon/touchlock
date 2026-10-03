package com.yeeyon.touchlock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.TouchInteractionController
import android.annotation.TargetApi
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager

/** Android 13 has no motionEventSources API. Keep its controller isolated from older OSes. */
@TargetApi(33)
internal class Android13TouchCapture(
    private val service: AccessibilityService,
    private val dispatch: (MotionEvent) -> Unit
) {
    private val controller = service.getTouchInteractionController(Display.DEFAULT_DISPLAY)
    private val handler = Handler(Looper.getMainLooper())
    private val accessibility = service.getSystemService(AccessibilityManager::class.java)
    private var active = false
    private val register: Runnable = object : Runnable {
        override fun run() {
            if (!active) return
            if (accessibility.isTouchExplorationEnabled) {
                // Android 13 drops registration until its asynchronously installed filter exists.
                // Re-registering the same callback reasserts detection without duplicating it.
                controller.registerCallback(null, callback)
            }
            handler.postDelayed(this, 200)
        }
    }
    private val callback: TouchInteractionController.Callback = object : TouchInteractionController.Callback {
        override fun onMotionEvent(event: MotionEvent) {
            // Receipt confirms the filter accepted registration; stop startup retries.
            handler.removeCallbacks(register)
            dispatch(event)
        }
        // Remaining in TOUCH_INTERACTING consumes the interaction. Never delegate it to apps.
        override fun onStateChanged(state: Int) {}
    }

    fun start() {
        active = true
        service.serviceInfo = service.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        }
        handler.post(register)
    }

    fun stop() {
        active = false
        handler.removeCallbacks(register)
        try {
            service.serviceInfo?.let {
                service.serviceInfo = it.apply {
                    flags = flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
                }
            }
        } finally {
            controller.unregisterCallback(callback)
        }
    }
}
