package com.esiri.esiriplus.service.overlay

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.esiri.esiriplus.MainActivity
import com.esiri.esiriplus.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a floating overlay bubble that persists across apps when the doctor is online.
 * The bubble pulses on new consultation requests and opens the app on tap.
 */
@Singleton
class OverlayBubbleManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val lock = Any()
    private var bubbleView: View? = null
    private var badgeView: View? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var currentRequestId: String? = null

    private val bubbleSizePx: Int =
        (BUBBLE_SIZE_DP * context.resources.displayMetrics.density).toInt()
    private val badgeSizePx: Int =
        (BADGE_SIZE_DP * context.resources.displayMetrics.density).toInt()

    fun show() {
        synchronized(lock) {
            if (bubbleView != null) return
            if (!Settings.canDrawOverlays(context)) return

            val container = createBubbleContainer()
            val params = createLayoutParams()

            try {
                windowManager.addView(container, params)
                bubbleView = container
            } catch (e: Exception) {
                // Permission may have been revoked between check and add
                bubbleView = null
            }
        }
    }

    fun hide() {
        synchronized(lock) {
            resetPulse()
            bubbleView?.let {
                try {
                    windowManager.removeView(it)
                } catch (_: Exception) { }
            }
            bubbleView = null
            badgeView = null
            currentRequestId = null
        }
    }

    fun showPulse(requestId: String) {
        synchronized(lock) {
            currentRequestId = requestId
            val view = bubbleView ?: return

            // Show badge
            badgeView?.visibility = View.VISIBLE

            // Start pulse animation
            pulseAnimator?.cancel()
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.3f, 1f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.3f, 1f)
            pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY).apply {
                duration = 800L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    fun resetPulse() {
        synchronized(lock) {
            pulseAnimator?.cancel()
            pulseAnimator = null
            badgeView?.visibility = View.GONE
            currentRequestId = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createBubbleContainer(): FrameLayout {
        val container = FrameLayout(context)

        // Main bubble circle
        val bubble = ImageView(context).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        container.addView(bubble, FrameLayout.LayoutParams(bubbleSizePx, bubbleSizePx))

        // Red badge dot (hidden by default)
        val badge = View(context).apply {
            setBackgroundResource(R.drawable.bg_badge_dot)
            visibility = View.GONE
        }
        val badgeParams = FrameLayout.LayoutParams(badgeSizePx, badgeSizePx).apply {
            gravity = Gravity.TOP or Gravity.END
        }
        container.addView(badge, badgeParams)
        badgeView = badge

        // Touch handling: drag + tap detection
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        container.setOnTouchListener { _, event ->
            val params = container.layoutParams as WindowManager.LayoutParams
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!isDragging && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(container, params)
                        } catch (_: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        onBubbleTapped()
                    } else {
                        snapToEdge(container, params)
                    }
                    true
                }
                else -> false
            }
        }

        return container
    }

    private fun snapToEdge(view: View, params: WindowManager.LayoutParams) {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val centerX = params.x + bubbleSizePx / 2

        params.x = if (centerX < screenWidth / 2) 0 else screenWidth - bubbleSizePx
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) { }
    }

    private fun onBubbleTapped() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ACTION, ACTION_INCOMING_REQUEST)
            currentRequestId?.let { putExtra(EXTRA_REQUEST_ID, it) }
        }
        context.startActivity(intent)
    }

    @Suppress("DEPRECATION")
    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            bubbleSizePx + badgeSizePx / 2,
            bubbleSizePx + badgeSizePx / 2,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = (200 * context.resources.displayMetrics.density).toInt()
        }
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val EXTRA_REQUEST_ID = "request_id"
        const val ACTION_INCOMING_REQUEST = "incoming_request"
        private const val BUBBLE_SIZE_DP = 56
        private const val BADGE_SIZE_DP = 12
        private const val DRAG_THRESHOLD = 10
    }
}
