package com.yeeyon.touchlock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import java.lang.ref.WeakReference
import java.io.FileDescriptor
import java.io.PrintWriter

/** Receives touchscreen input only during an explicitly requested touch lock. */
class TouchGuardService : AccessibilityService() {
    companion object {
        private var connection = WeakReference<TouchGuardService>(null)
        val instance: TouchGuardService? get() = connection.get()

        fun requested(context: Context): Boolean = Build.VERSION.SDK_INT >= 33 &&
            context.getSharedPreferences("settings", MODE_PRIVATE).getBoolean("systemGestureLock", true)

        fun ready(): Boolean = Build.VERSION.SDK_INT >= 33 && instance != null &&
            instance?.hasConflictingTouchExploration() == false
    }

    private var touchTarget: View? = null
    private var android13Capture: Android13TouchCapture? = null
    private var capturedTouchStarts = 0
    private val location = IntArray(2)
    private val explorationChanged = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
        if (enabled && touchTarget != null && hasConflictingTouchExploration()) {
            stopService(Intent(this, TouchLockService::class.java))
            releaseCapture()
        }
        sendBroadcast(Intent(TouchLockService.ACTION_STATE).setPackage(packageName))
    }

    private fun hasConflictingTouchExploration(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java)
        if (Build.VERSION.SDK_INT != 33 || touchTarget == null) return manager.isTouchExplorationEnabled
        // Android 13 requests exploration itself while locked. Only other services conflict.
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            it.resolveInfo.serviceInfo.packageName != packageName &&
                it.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE != 0
        }
    }

    override fun onServiceConnected() {
        connection = WeakReference(this)
        getSystemService(AccessibilityManager::class.java).addTouchExplorationStateChangeListener(explorationChanged)
        if (Build.VERSION.SDK_INT >= 34) {
            serviceInfo = serviceInfo.apply { motionEventSources = 0 }
        }
        serviceInfo = serviceInfo.apply {
            flags = flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
        }
        sendBroadcast(Intent(TouchLockService.ACTION_STATE).setPackage(packageName))
    }

    fun capture(target: View) {
        check(ready()) { "Touchscreen capture is unavailable" }
        touchTarget = target
        capturedTouchStarts = 0
        if (Build.VERSION.SDK_INT >= 34) {
            serviceInfo = serviceInfo.apply { motionEventSources = InputDevice.SOURCE_TOUCHSCREEN }
        } else if (Build.VERSION.SDK_INT == 33) {
            android13Capture = Android13TouchCapture(this, ::dispatchCapturedTouch)
            android13Capture?.start()
        }
    }

    fun releaseCapture() {
        val wasCapturing = touchTarget != null
        touchTarget = null
        val legacy = android13Capture
        android13Capture = null
        try { legacy?.stop() }
        catch (e: RuntimeException) { android.util.Log.w("TouchLock", "Touch controller disconnected", e) }
        if (wasCapturing && Build.VERSION.SDK_INT >= 34) {
            // The connection may already be gone when Android disables this service.
            try { serviceInfo?.let { serviceInfo = it.apply { motionEventSources = 0 } } }
            catch (e: RuntimeException) { android.util.Log.w("TouchLock", "Touch capture disconnected", e) }
        }
    }

    override fun onMotionEvent(event: MotionEvent) = dispatchCapturedTouch(event)

    private fun dispatchCapturedTouch(event: MotionEvent) {
        val target = touchTarget ?: return
        if (!target.isAttachedToWindow) return
        if (event.actionMasked == MotionEvent.ACTION_DOWN) capturedTouchStarts++
        target.getLocationOnScreen(location)
        val local = MotionEvent.obtain(event)
        local.offsetLocation(-location[0].toFloat(), -location[1].toFloat())
        try { target.dispatchTouchEvent(local) } finally { local.recycle() }
    }

    private fun disconnect() {
        getSystemService(AccessibilityManager::class.java).removeTouchExplorationStateChangeListener(explorationChanged)
        val wasCapturing = touchTarget != null
        releaseCapture()
        if (instance === this) connection.clear()
        if (wasCapturing) stopService(Intent(this, TouchLockService::class.java))
        sendBroadcast(Intent(TouchLockService.ACTION_STATE).setPackage(packageName))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        writer.println("TouchLock connected=${instance === this} capturing=${touchTarget != null}")
        writer.println("capturedTouchStarts=$capturedTouchStarts")
        writer.println("android13Controller=${android13Capture != null} touchExplorationRequested=${(serviceInfo?.flags ?: 0) and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE != 0}")
        if (Build.VERSION.SDK_INT >= 34) writer.println("motionEventSources=${serviceInfo?.motionEventSources ?: 0}")
    }
    override fun onInterrupt() {
        if (touchTarget != null) stopService(Intent(this, TouchLockService::class.java))
        releaseCapture()
    }
    override fun onUnbind(intent: Intent?): Boolean { disconnect(); return super.onUnbind(intent) }
    override fun onDestroy() { disconnect(); super.onDestroy() }
}
