package com.yeeyon.touchlock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.TouchInteractionController
import android.annotation.TargetApi
import android.view.Display
import android.view.MotionEvent

/** Android 13 has no motionEventSources API. Keep its controller isolated from older OSes. */
@TargetApi(33)
internal class Android13TouchCapture(
    private val service: AccessibilityService,
    private val dispatch: (MotionEvent) -> Unit
) {
    private val controller = service.getTouchInteractionController(Display.DEFAULT_DISPLAY)
    private val callback = object : TouchInteractionController.Callback {
        override fun onMotionEvent(event: MotionEvent) = dispatch(event)
        // Remaining in TOUCH_INTERACTING consumes the interaction. Never delegate it to apps.
        override fun onStateChanged(state: Int) {}
    }

    fun start() {
        controller.registerCallback(null, callback)
        service.serviceInfo = service.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        }
    }

    fun stop() {
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
