package com.yeeyon.touchlock

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val navy = Color.rgb(16, 38, 60)
    private val teal = Color.rgb(0, 112, 124)
    private val muted = Color.rgb(74, 95, 114)
    private lateinit var stateTitle: TextView
    private lateinit var stateDetail: TextView
    private lateinit var overlayState: TextView
    private lateinit var overlayButton: Button
    private lateinit var notificationState: TextView
    private lateinit var notificationButton: Button
    private lateinit var enableButton: Button
    private lateinit var stopButton: Button
    private var receiverRegistered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.rgb(243, 247, 251)) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
        }
        scroll.addView(content)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(scroll)

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply { setImageResource(R.drawable.ic_launcher); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(52), dp(52)))
        header.addView(text("TouchLock", 29f, navy, true).apply { setPadding(dp(14), 0, 0, 0) })
        content.addView(header)
        content.addView(text("Keep watching. Block accidental touches.", 17f, muted).apply { setPadding(0, dp(18), 0, dp(24)) })

        val status = card(navy)
        stateTitle = text("TouchLock is off", 23f, Color.WHITE, true)
        stateDetail = text("Enable the floating button, then open your video.", 16f, Color.rgb(202, 221, 237))
        status.addView(stateTitle)
        status.addView(stateDetail.apply { setPadding(0, dp(10), 0, dp(18)) })
        enableButton = button("Enable floating lock", Color.rgb(76, 224, 220), navy) { enable() }
        status.addView(enableButton)
        stopButton = button("Stop TouchLock", Color.rgb(46, 68, 89), Color.WHITE) {
            stopService(Intent(this, TouchLockService::class.java))
        }
        status.addView(stopButton.apply { visibility = View.GONE }, rowParams(10))
        content.addView(status)

        content.addView(text("SETUP", 13f, teal, true).apply { setPadding(0, dp(26), 0, dp(12)); letterSpacing = 0.12f })
        val permissions = card(Color.WHITE)
        permissions.addView(text("Display over other apps", 18f, navy, true))
        overlayState = text("Required to block taps over your video.", 15f, muted)
        permissions.addView(overlayState.apply { setPadding(0, dp(6), 0, dp(8)) })
        overlayButton = button("Allow display over apps", Color.rgb(225, 244, 245), teal) { openOverlaySettings() }
        permissions.addView(overlayButton)
        permissions.addView(View(this).apply { setBackgroundColor(Color.rgb(229, 236, 243)) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(20); bottomMargin = dp(20) })
        permissions.addView(text("Notification controls", 18f, navy, true))
        notificationState = text("Lock from the notification, or stop TouchLock.", 15f, muted)
        permissions.addView(notificationState.apply { setPadding(0, dp(6), 0, dp(8)) })
        notificationButton = button("Allow notifications", Color.rgb(225, 244, 245), teal) { allowNotifications() }
        permissions.addView(notificationButton)
        content.addView(permissions)

        content.addView(text("HOW TO USE", 13f, teal, true).apply { setPadding(0, dp(26), 0, dp(12)); letterSpacing = 0.12f })
        val instructions = card(Color.WHITE)
        step(instructions, "1", "Play your video", "Enable the floating lock, then open WhatsApp or another video app.")
        step(instructions, "2", "Tap to lock", "The open padlock means ready. Tap it or use Lock screen touch in the notification. Drag the button to reposition it, even while locked.")
        step(instructions, "3", "Hold 3 seconds to unlock", "Hold the closed padlock still until the ring fills. Releasing early or dragging keeps touch locked. Use Show button in the notification if you lose the control.")
        content.addView(instructions)
        content.addView(text("Blocks touches inside the app. System navigation, notifications, and hardware buttons remain available. Stop TouchLock from its notification if needed. Turning the screen off stops TouchLock.", 14f, muted).apply { setPadding(0, dp(20), 0, dp(16)) })
        content.addView(text("Offline · No account · No accessibility access", 13f, teal, true))
        refresh()
    }

    private fun step(parent: LinearLayout, number: String, title: String, detail: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(12)) }
        row.addView(text(number, 18f, teal, true).apply { gravity = android.view.Gravity.CENTER; background = shape(Color.rgb(225, 244, 245), 12) }, LinearLayout.LayoutParams(dp(36), dp(36)))
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        copy.addView(text(title, 17f, navy, true))
        copy.addView(text(detail, 15f, muted).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(row)
    }

    private fun enable() {
        if (!Settings.canDrawOverlays(this)) { openOverlaySettings(); return }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getPreferences(MODE_PRIVATE).getBoolean("notificationAsked", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
        try {
            startForegroundService(Intent(this, TouchLockService::class.java).setAction(TouchLockService.ACTION_ENABLE))
        } catch (e: RuntimeException) {
            Toast.makeText(this, "TouchLock could not start. Check its permissions and try again.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "Open Settings, then Apps, TouchLock, Display over other apps.", Toast.LENGTH_LONG).show()
        }
    }

    private fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            (!getPreferences(MODE_PRIVATE).getBoolean("notificationAsked", false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        } else {
            try { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }
            catch (e: android.content.ActivityNotFoundException) { Toast.makeText(this, "Allow notifications in TouchLock's app settings.", Toast.LENGTH_LONG).show() }
        }
    }

    private fun refresh() {
        val allowed = Settings.canDrawOverlays(this)
        overlayState.text = if (allowed) "Allowed. TouchLock can show the floating control." else "Required to block taps over your video."
        overlayButton.text = if (allowed) "Manage permission" else "Allow display over apps"
        val notifications = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        notificationState.text = if (notifications) "Allowed. Lock, show button, and stop controls are available." else "Allow to lock from the panel and restore a hidden button."
        notificationButton.text = if (notifications) "Manage notifications" else "Allow notifications"
        stateTitle.text = when { TouchLockService.locked -> "Screen touch locked"; TouchLockService.running -> "Ready when you are"; else -> "TouchLock is off" }
        stateDetail.text = when { TouchLockService.locked -> "Hold the floating lock for 3 seconds to unlock."; TouchLockService.running -> "Open your video, then tap the floating lock."; else -> "Enable the floating button, then open your video." }
        enableButton.text = if (!allowed) "Set up floating lock" else "Enable floating lock"
        enableButton.visibility = if (TouchLockService.running) View.GONE else View.VISIBLE
        stopButton.visibility = if (TouchLockService.running) View.VISIBLE else View.GONE
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, IntentFilter(TouchLockService.ACTION_STATE), RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, IntentFilter(TouchLockService.ACTION_STATE))
        }
        receiverRegistered = true
    }
    override fun onResume() { super.onResume(); refresh() }
    override fun onStop() {
        if (receiverRegistered) { unregisterReceiver(receiver); receiverRegistered = false }
        super.onStop()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        setLineSpacing(dp(3).toFloat(), 1f)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun card(color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(color, 22)
        setPadding(dp(20), dp(20), dp(20), dp(20))
        layoutParams = rowParams()
    }
    private fun rowParams(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun button(label: String, backgroundColor: Int, textColor: Int, click: () -> Unit) = Button(this).apply {
        text = label; textSize = 16f; isAllCaps = false
        minHeight = dp(52); minimumHeight = dp(52)
        backgroundTintList = ColorStateList.valueOf(backgroundColor)
        setTextColor(textColor)
        setOnClickListener { click() }
    }
}
