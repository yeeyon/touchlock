package com.yeeyon.touchlock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.ceil

/**
 * A single-finger hold, or a tap when [holdMs] is 0. Moving outside, an extra finger, or
 * cancellation resets it. [step] labels the control as part of a multi-step unlock.
 */
internal class LockControlView(
    context: Context,
    private val onUnlock: () -> Unit,
    private val onDrag: (Float, Float) -> Unit,
    private val onDragEnd: () -> Unit,
    private val movable: Boolean = true,
    private val holdMs: Long = 3_000L,
    private val step: String? = null
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapOnly = holdMs <= 0L
    private val hold = HoldGesture(holdMs.coerceAtLeast(1L))
    /** Set once this control has unlocked; later events are ignored. */
    private var fired = false
    private var holding = false
    private var rejected = false
    private var activePointer = -1
    private var hintUntil = 0L
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragging = false
    private var multiplePointers = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val resetHint = Runnable { invalidate() }
    private val tick = object : Runnable {
        override fun run() {
            if (!holding) return
            if (hold.isComplete(SystemClock.uptimeMillis())) {
                holding = false
                hold.cancel()
                complete()
            } else {
                invalidate()
                postOnAnimation(this)
            }
        }
    }

    init {
        val action = if (tapOnly) "Tap it" else "Hold still for three seconds"
        contentDescription = when {
            step != null -> "Touch locked. Unlock step $step. $action."
            movable -> "Touch locked. Hold still for three seconds to unlock, or drag to reposition."
            else -> "Touch locked. $action to unlock."
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = true
    }

    private fun complete() {
        if (fired) return
        fired = true
        performHapticFeedback(if (tapOnly) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.LONG_PRESS)
        onUnlock()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (fired) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                rejected = false
                activePointer = event.getPointerId(0)
                downRawX = event.rawX; downRawY = event.rawY
                lastRawX = event.rawX; lastRawY = event.rawY
                dragging = false; multiplePointers = false
                hold.begin(SystemClock.uptimeMillis())
                holding = true
                removeCallbacks(resetHint)
                hintUntil = 0L
                // A tap completes on release, so it needs no progress frames.
                if (!tapOnly) postOnAnimation(tick)
            }
            MotionEvent.ACTION_POINTER_DOWN -> { multiplePointers = true; cancelHold() }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointer)
                if (index < 0 || event.pointerCount != 1 || multiplePointers) {
                    cancelHold()
                } else if (!movable && (abs(event.rawX - downRawX) > touchSlop || abs(event.rawY - downRawY) > touchSlop)) {
                    cancelHold()
                } else if (movable && (dragging || abs(event.rawX - downRawX) > touchSlop || abs(event.rawY - downRawY) > touchSlop)) {
                    dragging = true
                    cancelHold(false)
                    onDrag(event.rawX - lastRawX, event.rawY - lastRawY)
                    lastRawX = event.rawX; lastRawY = event.rawY
                } else if (event.getX(index) < 0 ||
                    event.getX(index) > width || event.getY(index) < 0 || event.getY(index) > height) {
                    cancelHold()
                }
            }
            MotionEvent.ACTION_UP -> {
                // A frame may not have run at the threshold; use the actual release time too.
                val completed = holding && !rejected && (tapOnly || hold.isComplete(SystemClock.uptimeMillis()))
                cancelHold(showHint = !completed)
                if (dragging) onDragEnd()
                if (completed) complete()
                activePointer = -1
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelHold()
                if (dragging) onDragEnd()
                dragging = false
            }
            MotionEvent.ACTION_POINTER_UP -> cancelHold()
        }
        invalidate()
        return true
    }

    private fun cancelHold(showHint: Boolean = true) {
        if (holding && showHint) {
            hintUntil = SystemClock.uptimeMillis() + 900
            removeCallbacks(resetHint)
            postDelayed(resetHint, 900)
        }
        holding = false
        rejected = true
        hold.cancel()
        removeCallbacks(tick)
    }

    override fun onDetachedFromWindow() {
        cancelHold(false)
        removeCallbacks(resetHint)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = 40 * density
        val radius = 33 * density
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(16, 38, 60)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3 * density
        paint.color = Color.rgb(61, 84, 104)
        val ring = RectF(cx - radius + 3 * density, cy - radius + 3 * density,
            cx + radius - 3 * density, cy + radius - 3 * density)
        canvas.drawOval(ring, paint)
        paint.color = if (hintUntil > SystemClock.uptimeMillis()) Color.rgb(255, 172, 102)
            else Color.rgb(76, 224, 220)
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(ring, -90f, hold.progress(SystemClock.uptimeMillis()) * 360f, false, paint)
        paint.strokeCap = Paint.Cap.BUTT
        drawLock(canvas, paint, cx, cy, density, Color.WHITE)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12 * density
        paint.isFakeBoldText = true
        val seconds = (holdMs / 1000).coerceAtLeast(1)
        val action = when {
            tapOnly -> "TAP"
            holding -> "${ceil(seconds * (1 - hold.progress(SystemClock.uptimeMillis()))).toInt()}s"
            else -> "HOLD ${seconds}s"
        }
        val label = if (step != null) "$step $action" else action
        // Shrink long step labels to fit the pill.
        while (paint.measureText(label) > 72 * density && paint.textSize > 8 * density) paint.textSize -= 0.5f * density
        val bounds = RectF(cx - 40 * density, 79 * density, cx + 40 * density, 104 * density)
        paint.color = Color.rgb(16, 38, 60)
        canvas.drawRoundRect(bounds, 12 * density, 12 * density, paint)
        paint.color = Color.WHITE
        canvas.drawText(label, cx, 96 * density, paint)
        paint.isFakeBoldText = false
    }
}

internal fun drawLock(canvas: Canvas, paint: Paint, cx: Float, cy: Float, d: Float, color: Int, closed: Boolean = true) {
    paint.color = color
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2.3f * d
    val shift = if (closed) 0f else 8 * d
    canvas.drawArc(RectF(cx - 7 * d + shift, cy - 13 * d, cx + 7 * d + shift, cy + 3 * d), 180f, 180f, false, paint)
    paint.style = Paint.Style.FILL
    canvas.drawRoundRect(RectF(cx - 11 * d, cy - 3 * d, cx + 11 * d, cy + 14 * d), 3 * d, 3 * d, paint)
    paint.color = Color.rgb(16, 38, 60)
    canvas.drawCircle(cx, cy + 4 * d, 2 * d, paint)
    canvas.drawRect(cx - d, cy + 4 * d, cx + d, cy + 9 * d, paint)
}

internal class FloatingLockView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        contentDescription = "Lock screen touch. Tap to lock; drag to reposition."
        isClickable = true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        paint.color = Color.rgb(16, 38, 60)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 29 * d, paint)
        paint.color = Color.rgb(76, 224, 220)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2 * d
        canvas.drawCircle(cx, cy, 25 * d, paint)
        drawLock(canvas, paint, cx, cy, d, Color.rgb(76, 224, 220), closed = false)
    }
}
