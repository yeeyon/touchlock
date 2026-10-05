package com.yeeyon.touchlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.roundToInt

class TouchLockService : Service() {
    companion object {
        const val ACTION_ENABLE = "com.yeeyon.touchlock.ENABLE"
        const val ACTION_LOCK = "com.yeeyon.touchlock.LOCK"
        const val ACTION_SHOW = "com.yeeyon.touchlock.SHOW"
        const val ACTION_STOP = "com.yeeyon.touchlock.STOP"
        const val ACTION_STATE = "com.yeeyon.touchlock.STATE"
        @Volatile var running = false
            private set
        @Volatile var locked = false
            private set
        private const val CHANNEL = "touchlock_controls"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var wm: WindowManager
    private var bubble: FloatingLockView? = null
    private var blocker: FrameLayout? = null
    private var blockerManager: WindowManager? = null
    private var captureGuard: TouchGuardService? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private val prefs by lazy { getSharedPreferences("position", MODE_PRIVATE) }
    private var rightSide = true
    private var yFraction = 0.6f
    private var receiverRegistered = false
    /** Step-unlock route for the current lock, or null for the single hold button. */
    private var steps: UnlockSteps? = null
    private var stepIndex = 0
    private val random = java.util.Random()
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { stopSelf() }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        rightSide = prefs.getBoolean("rightSide", true)
        yFraction = prefs.getFloat("yFraction", 0.6f).coerceIn(0f, 1f)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "TouchLock controls", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Lock screen touch or stop the floating control."
                setShowBadge(false)
            })
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (!Settings.canDrawOverlays(this)) {
            fail("Allow TouchLock to display over other apps first.")
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
            running = true
            when (intent?.action) {
                ACTION_LOCK -> lock()
                ACTION_SHOW -> restoreControl()
                else -> if (!locked && bubble == null) showBubble()
            }
            publishState()
        } catch (e: RuntimeException) {
            android.util.Log.e("TouchLock", "Cannot show touch lock", e)
            fail("TouchLock could not start. Check display-over-other-apps permission, then try again.")
        }
        return START_NOT_STICKY
    }

    private fun overlayParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.LEFT; title = "TouchLock" }

    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = wm.currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private fun showBubble() {
        if (bubble != null) return
        val view = FloatingLockView(this)
        val size = dp(72)
        val (sw, sh) = screenSize()
        val params = overlayParams(size, size).apply {
            title = "TouchLock floating button"
            x = if (rightSide) (sw - size).coerceAtLeast(0) else 0
            y = ((sh - size).coerceAtLeast(0) * yFraction).roundToInt()
        }
        var downX = 0f
        var downY = 0f
        var initialX = 0
        var initialY = 0
        var moved = false
        var cancelled = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        view.setOnClickListener { lockSafely() }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    initialX = params.x; initialY = params.y
                    moved = false; cancelled = false
                }
                MotionEvent.ACTION_POINTER_DOWN -> cancelled = true
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - downX) > slop || abs(event.rawY - downY) > slop) moved = true
                    if (moved && !cancelled) {
                        val (w, h) = screenSize()
                        params.x = (initialX + event.rawX - downX).roundToInt().coerceIn(0, (w - size).coerceAtLeast(0))
                        params.y = (initialY + event.rawY - downY).roundToInt().coerceIn(dp(24), (h - size - dp(24)).coerceAtLeast(dp(24)))
                        wm.updateViewLayout(view, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && !cancelled) view.performClick() else snapBubble()
                }
                MotionEvent.ACTION_CANCEL -> { cancelled = true; snapBubble() }
            }
            true
        }
        wm.addView(view, params)
        bubble = view
        bubbleParams = params
    }

    private fun snapBubble() {
        val view = bubble ?: return
        val params = bubbleParams ?: return
        val (w, h) = screenSize()
        rightSide = params.x + params.width / 2 > w / 2
        params.x = if (rightSide) (w - params.width).coerceAtLeast(0) else 0
        yFraction = params.y.toFloat() / (h - params.height).coerceAtLeast(1)
        prefs.edit().putBoolean("rightSide", rightSide).putFloat("yFraction", yFraction).apply()
        wm.updateViewLayout(view, params)
    }

    private fun lockSafely() {
        try { lock() } catch (e: RuntimeException) {
            android.util.Log.e("TouchLock", "Cannot attach overlay", e)
            fail("The touch blocker could not open. Enable display-over-other-apps permission and try again.")
        }
    }

    private fun lock() {
        if (locked) return
        val strong = TouchGuardService.requested(this)
        if (strong && !TouchGuardService.ready()) {
            Toast.makeText(this, "Enable TouchLock gesture protection in Accessibility. Turn off other touch exploration services to use gesture protection.", Toast.LENGTH_LONG).show()
            return
        }
        val guard = if (strong) TouchGuardService.instance else null
        val windowContext: Context = guard ?: this
        val manager = windowContext.getSystemService(WindowManager::class.java)
        val root = object : FrameLayout(windowContext) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                post { positionUnlock(this) }
            }
        }.apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            isMotionEventSplittingEnabled = false
            isClickable = true
            setOnTouchListener { _, _ -> true }
            keepScreenOn = true
        }
        steps = if (stepUnlockEnabled(this)) UnlockSteps.plan(random) else null
        stepIndex = 0
        val unlock = steps?.let { stepControl(windowContext, root, it) } ?: LockControlView(windowContext, { unlockSafely() }, { dx, dy ->
            val child = root.getChildAt(0)
            val layout = child.layoutParams as FrameLayout.LayoutParams
            layout.leftMargin = (layout.leftMargin + dx).roundToInt().coerceIn(0, (root.width - layout.width).coerceAtLeast(0))
            layout.topMargin = (layout.topMargin + dy).roundToInt().coerceIn(dp(24), (root.height - layout.height - dp(24)).coerceAtLeast(dp(24)))
            child.layoutParams = layout
        }, {
            val layout = root.getChildAt(0).layoutParams as FrameLayout.LayoutParams
            rightSide = layout.leftMargin + layout.width / 2 > root.width / 2
            yFraction = (layout.topMargin.toFloat() / (root.height - layout.height).coerceAtLeast(1)).coerceIn(0f, 1f)
            savePosition()
            positionUnlock(root)
        })
        root.addView(unlock, FrameLayout.LayoutParams(dp(92), dp(112)))
        root.setOnApplyWindowInsetsListener { _, insets -> positionUnlock(root); insets }
        val params = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        params.title = "TouchLock touch blocker"
        if (guard != null) {
            params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            if (Build.VERSION.SDK_INT >= 30) {
                params.setFitInsetsTypes(0)
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        // Never FLAG_NOT_TOUCHABLE: this window deliberately consumes app-area touches.
        manager.addView(root, params)
        blocker = root
        blockerManager = manager
        captureGuard = guard
        guard?.capture(root)
        locked = true
        removeBubble()
        updateNotification()
        publishState()
    }

    /** The control for the current step. Finishing it shows the next one, or unlocks after the last. */
    private fun stepControl(context: Context, root: FrameLayout, plan: UnlockSteps): LockControlView {
        val index = stepIndex
        return LockControlView(context, {
            // Swap after this touch dispatch finishes, never while it is in progress.
            root.post {
                if (blocker !== root || stepIndex != index) return@post
                if (index + 1 >= plan.count) { unlockSafely(); return@post }
                stepIndex = index + 1
                root.removeAllViews()
                root.addView(stepControl(context, root, plan), FrameLayout.LayoutParams(dp(92), dp(112)))
                positionUnlock(root)
            }
        }, { _, _ -> }, {}, movable = false,
            holdMs = if (plan.needsHold(index)) 3_000L else 0L,
            step = "${index + 1}/${plan.count}")
    }

    private fun positionUnlock(root: FrameLayout) {
        if (root.childCount == 0 || root.width == 0) return
        val child = root.getChildAt(0)
        var top = dp(24); var bottom = dp(24); var left = 0; var right = 0
        root.rootWindowInsets?.let { insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                top = maxOf(top, safe.top); bottom = maxOf(bottom, safe.bottom)
                left = safe.left; right = safe.right
            } else {
                @Suppress("DEPRECATION")
                top = maxOf(top, insets.systemWindowInsetTop)
                @Suppress("DEPRECATION")
                bottom = maxOf(bottom, insets.systemWindowInsetBottom)
            }
        }
        val params = child.layoutParams as FrameLayout.LayoutParams
        val maxX = (root.width - params.width - right).coerceAtLeast(left)
        val maxY = (root.height - params.height - bottom).coerceAtLeast(top)
        val spot = steps?.spots?.getOrNull(stepIndex)
        if (spot != null) {
            // Fixed fractions of the safe area: the step stays put through rotation and insets.
            params.leftMargin = (left + (maxX - left) * spot.first).roundToInt()
            params.topMargin = (top + (maxY - top) * spot.second).roundToInt()
        } else {
            params.leftMargin = if (rightSide) maxX else left
            params.topMargin = ((root.height - params.height) * yFraction).roundToInt().coerceIn(top, maxY)
        }
        child.layoutParams = params
    }

    private fun unlockSafely() {
        try {
            // Add the small control before removing the blocker; no touch leaks from the hold.
            showBubble()
            removeBlocker()
            locked = false
            updateNotification()
            publishState()
        } catch (e: RuntimeException) {
            android.util.Log.e("TouchLock", "Cannot restore floating control", e)
            fail("TouchLock stopped. Open the app to enable it again.")
        }
    }

    private fun savePosition() {
        prefs.edit().putBoolean("rightSide", rightSide).putFloat("yFraction", yFraction).apply()
    }

    private fun restoreControl() {
        rightSide = true
        yFraction = 0.6f
        savePosition()
        if (locked) blocker?.let { positionUnlock(it) }
        else { removeBubble(); showBubble() }
        updateNotification()
    }

    private fun removeBubble() {
        bubble?.let { if (it.isAttachedToWindow) wm.removeViewImmediate(it) }
        bubble = null; bubbleParams = null
    }
    private fun removeBlocker() {
        captureGuard?.releaseCapture()
        captureGuard = null
        blocker?.let { if (it.isAttachedToWindow) blockerManager?.removeViewImmediate(it) }
        blocker = null
        blockerManager = null
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, TouchLockService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(if (locked) "Screen touch locked" else "TouchLock ready")
            .setContentText(if (locked && steps != null) "Complete 3 unlock buttons. One needs a 3-second hold."
                else if (locked) "Hold the floating lock for 3 seconds to unlock." else "Tap the floating lock when your video is playing.")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
        if (!locked) {
            val lock = PendingIntent.getForegroundService(this, 2, Intent(this, TouchLockService::class.java).setAction(ACTION_LOCK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(Notification.Action.Builder(R.drawable.ic_lock, "Lock screen touch", lock).build())
        }
        val show = PendingIntent.getForegroundService(this, 3, Intent(this, TouchLockService::class.java).setAction(ACTION_SHOW), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        builder.addAction(Notification.Action.Builder(R.drawable.ic_lock, "Show button", show).build())
        builder.addAction(Notification.Action.Builder(0, "Stop TouchLock", stop).build())
        return builder.build()
    }

    private fun updateNotification() { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }
    private fun publishState() { sendBroadcast(Intent(ACTION_STATE).setPackage(packageName)) }
    private fun fail(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); stopSelf() }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!locked && bubble != null) {
            try { removeBubble(); showBubble() } catch (e: RuntimeException) { fail("TouchLock stopped. Enable it again from the app.") }
        }
        // The full-screen window remeasures and positions its unlock control automatically.
    }

    override fun onDestroy() {
        removeBlocker()
        removeBubble()
        if (receiverRegistered) unregisterReceiver(screenOff)
        locked = false; running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        publishState()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
