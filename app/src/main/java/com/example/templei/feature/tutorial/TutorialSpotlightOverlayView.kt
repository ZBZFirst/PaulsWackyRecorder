package com.example.templei.feature.tutorial

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.lang.ref.WeakReference

/**
 * Full-screen tutorial overlay that dims the UI and forwards touches only to
 * the currently highlighted targets.
 */
class TutorialSpotlightOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(206, 7, 10, 16)
    }
    private val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 244, 213)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val cutoutPaddingPx = dp(12f)
    private val cornerRadiusPx = dp(20f)
    private val highlightBounds = mutableListOf<Pair<WeakReference<View>, RectF>>()
    private var activeTouchTarget: WeakReference<View>? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }

    fun showTargets(targets: List<View>) {
        highlightBounds.clear()
        targets.distinct().forEach { target ->
            highlightBounds += WeakReference(target) to RectF()
        }
        visibility = if (highlightBounds.isEmpty()) GONE else VISIBLE
        invalidate()
    }

    fun clearTargets() {
        activeTouchTarget = null
        highlightBounds.clear()
        visibility = GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE || highlightBounds.isEmpty()) return

        refreshBounds()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        highlightBounds.forEach { (_, rect) ->
            if (!rect.isEmpty) {
                canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, cutoutPaint)
                canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, outlinePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE || highlightBounds.isEmpty()) {
            return false
        }

        val target = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> findTargetAt(event.x, event.y)?.also {
                activeTouchTarget = WeakReference(it)
            }
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> activeTouchTarget?.get()
            else -> null
        } ?: return true

        val forwarded = MotionEvent.obtain(event)
        forwarded.offsetLocation(
            xOffsetToTarget(target),
            yOffsetToTarget(target),
        )
        target.dispatchTouchEvent(forwarded)
        forwarded.recycle()

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            activeTouchTarget = null
        }
        return true
    }

    private fun refreshBounds() {
        val overlayRect = Rect()
        getGlobalVisibleRect(overlayRect)
        highlightBounds.forEach { (targetRef, rect) ->
            val target = targetRef.get()
            if (target == null || !target.isShown) {
                rect.setEmpty()
            } else {
                rect.set(resolveBounds(target, overlayRect))
            }
        }
    }

    private fun resolveBounds(target: View, overlayRect: Rect): RectF {
        val targetRect = Rect()
        if (!target.getGlobalVisibleRect(targetRect)) {
            return RectF()
        }
        return RectF(
            (targetRect.left - overlayRect.left - cutoutPaddingPx).coerceAtLeast(0f),
            (targetRect.top - overlayRect.top - cutoutPaddingPx).coerceAtLeast(0f),
            (targetRect.right - overlayRect.left + cutoutPaddingPx).coerceAtMost(width.toFloat()),
            (targetRect.bottom - overlayRect.top + cutoutPaddingPx).coerceAtMost(height.toFloat()),
        )
    }

    private fun findTargetAt(x: Float, y: Float): View? {
        refreshBounds()
        return highlightBounds.firstOrNull { (_, rect) -> rect.contains(x, y) }?.first?.get()
    }

    private fun xOffsetToTarget(target: View): Float {
        val overlayLocation = IntArray(2)
        val targetLocation = IntArray(2)
        getLocationOnScreen(overlayLocation)
        target.getLocationOnScreen(targetLocation)
        return (overlayLocation[0] - targetLocation[0]).toFloat()
    }

    private fun yOffsetToTarget(target: View): Float {
        val overlayLocation = IntArray(2)
        val targetLocation = IntArray(2)
        getLocationOnScreen(overlayLocation)
        target.getLocationOnScreen(targetLocation)
        return (overlayLocation[1] - targetLocation[1]).toFloat()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
