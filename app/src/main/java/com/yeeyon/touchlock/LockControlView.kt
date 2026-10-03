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

/** A single-finger hold. Moving outside, an extra finger, or cancellation resets it. */
internal class LockControlView(
    context: Context,
    private val onUnlock: () -> Unit,
    private val onDrag: (Float, Float) -> Unit,
    private val onDragEnd: () -> Unit
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hold = HoldGesture()
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
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onUnlock()
            } else {
                invalidate()
                postOnAnimation(this)
            }
        }
    }

    init {
        contentDescription = "Touch locked. Hold still for three seconds to unlock, or drag to reposition."
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
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
                postOnAnimation(tick)
            }
            MotionEvent.ACTION_POINTER_DOWN -> { multiplePointers = true; cancelHold() }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointer)
                if (index < 0 || event.pointerCount != 1 || multiplePointers) {
                    cancelHold()
                } else if (dragging || abs(event.rawX - downRawX) > touchSlop || abs(event.rawY - downRawY) > touchSlop) {
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
                val completed = holding && !rejected && hold.isComplete(SystemClock.uptimeMillis())
                cancelHold(showHint = !completed)
                if (dragging) onDragEnd()
                if (completed) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onUnlock()
                }
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
        val label = if (holding) "${ceil(3 * (1 - hold.progress(SystemClock.uptimeMillis()))).toInt()}s"
            else "HOLD 3s"
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
