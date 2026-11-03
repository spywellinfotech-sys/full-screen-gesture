package com.spywell.fullscreengesture

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class GestureService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private enum class Edge { LEFT, RIGHT, BOTTOM }

    private lateinit var wm: WindowManager
    private val strips = mutableListOf<View>()

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(this)
        rebuild()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rebuild()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) = rebuild()

    override fun onDestroy() {
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(this)
        removeStrips()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun removeStrips() {
        strips.forEach { runCatching { wm.removeView(it) } }
        strips.clear()
    }

    private fun rebuild() {
        if (!::wm.isInitialized) return
        removeStrips()
        val screenH = resources.displayMetrics.heightPixels
        val edgeW = dp(Prefs.edgeDp(this))
        val bottomH = dp(Prefs.bottomDp(this))

        if (Prefs.bool(this, Prefs.LEFT)) {
            add(Edge.LEFT, edgeW, screenH - bottomH, Gravity.TOP or Gravity.START)
        }
        if (Prefs.bool(this, Prefs.RIGHT)) {
            add(Edge.RIGHT, edgeW, screenH - bottomH, Gravity.TOP or Gravity.END)
        }
        if (Prefs.bool(this, Prefs.BOTTOM)) {
            add(Edge.BOTTOM, WindowManager.LayoutParams.MATCH_PARENT, bottomH, Gravity.BOTTOM)
        }
    }

    private fun add(edge: Edge, w: Int, h: Int, gravity: Int) {
        val view = StripView(edge)
        val lp = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = gravity
        runCatching {
            wm.addView(view, lp)
            strips.add(view)
        }
    }

    private fun buzz(v: View) {
        if (Prefs.bool(this, Prefs.HAPTIC)) {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    private inner class StripView(private val edge: Edge) : View(this@GestureService) {
        private val handler = Handler(Looper.getMainLooper())
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var fired = false
        private var armed = false

        private val backDist = dp(70)
        private val homeDist = dp(60)
        private val holdMove = dp(6)

        private val recentsRunnable = Runnable {
            if (armed && !fired) {
                fired = true
                buzz(this)
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    lastX = downX; lastY = downY
                    fired = false; armed = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (fired) return true
                    when (edge) {
                        Edge.LEFT, Edge.RIGHT -> {
                            val dx = if (edge == Edge.LEFT) e.rawX - downX else downX - e.rawX
                            if (dx > backDist) {
                                fired = true
                                buzz(this)
                                performGlobalAction(GLOBAL_ACTION_BACK)
                            }
                        }
                        Edge.BOTTOM -> {
                            val dy = downY - e.rawY
                            if (dy > homeDist) {
                                armed = true
                                // Swipe up and pause = Recents. Any further movement resets the pause.
                                if (Math.abs(e.rawY - lastY) > holdMove || Math.abs(e.rawX - lastX) > holdMove) {
                                    lastX = e.rawX; lastY = e.rawY
                                    handler.removeCallbacks(recentsRunnable)
                                    handler.postDelayed(recentsRunnable, 450)
                                }
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(recentsRunnable)
                    if (edge == Edge.BOTTOM && armed && !fired) {
                        fired = true
                        buzz(this)
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(recentsRunnable)
            }
            return true
        }
    }
}
