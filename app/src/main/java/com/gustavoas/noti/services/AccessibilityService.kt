package com.gustavoas.noti.services

import android.accessibilityservice.AccessibilityService
import android.animation.ObjectAnimator
import android.app.KeyguardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.gustavoas.noti.Permissions.hasAccessibilityPermission
import com.gustavoas.noti.R
import kotlin.math.roundToInt

class AccessibilityService : AccessibilityService() {
    private lateinit var overlayView: View
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var circularProgressBar: CircularProgressIndicator
    private val handler = Handler(Looper.getMainLooper())
    private var toBeRemoved = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasAccessibilityPermission(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            return super.onStartCommand(intent, flags, startId)
        }

        val progress = intent?.getIntExtra("progress", 0)
        val progressMax = intent?.getIntExtra("progressMax", 0)
        val removal = intent?.getBooleanExtra("removal", false)

        val showInLockScreen = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("showInLockScreen", true)

        if (removal != null && removal) {
            if (!this::overlayView.isInitialized || !overlayView.isShown) {
                return super.onStartCommand(intent, flags, startId)
            }
            toBeRemoved = true
            hideProgressBarIn(1000)
        } else if (progress != null && progressMax != null
            && (!isLocked() || showInLockScreen)) {
            showOverlayWithProgress(progress, progressMax)
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun showOverlayWithProgress(progress: Int, progressMax: Int) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        val useOnlyInPortrait = sharedPreferences.getBoolean("onlyInPortrait", false)
        val useCircularProgressBar = (sharedPreferences.getString("progressBarStyle", "linear") == "circular" && (!useOnlyInPortrait || isInPortraitMode()))

        if (!this::overlayView.isInitialized || !overlayView.isShown) {
            if (!useCircularProgressBar) {
                val showBelowNotch = sharedPreferences.getBoolean("showBelowNotch", false)
                inflateOverlay(showBelowNotch)
            } else {
                inflateOverlay()
            }
        }

        progressBar = overlayView.findViewById(R.id.progressBar)
        circularProgressBar = overlayView.findViewById(R.id.circularProgressBar)

        if (useCircularProgressBar) {
            progressBar.visibility = View.GONE
            circularProgressBar.visibility = View.VISIBLE

            circularProgressBarCustomizations(sharedPreferences)
        } else {
            progressBar.visibility = View.VISIBLE
            circularProgressBar.visibility = View.GONE

            linearProgressBarCustomizations(sharedPreferences)
        }

        applyCommonProgressBarCustomizations(sharedPreferences)

        val progressBarMax = progressBar.max
        val currentProgress = (progress.toDouble()/progressMax.toDouble() * progressBarMax).roundToInt()

        when (currentProgress) {
            0 -> {
                setProgressToZero()
                return
            }
            progressBarMax -> {
                animateProgressBarTo(progressBarMax, useCircularProgressBar)
            }
            else -> {
                if (currentProgress < progressBar.progress && progressBar.progress != progressBarMax && !toBeRemoved) {
                    return
                }
                toBeRemoved = false
                animateProgressBarTo(currentProgress, useCircularProgressBar)
            }
        }

        hideProgressBarIn(10000)
    }

    private fun circularProgressBarCustomizations(sharedPreferences: SharedPreferences) {
        val container = overlayView.findViewById<LinearLayout>(R.id.container)
        when (sharedPreferences.getString("progressBarLocation", "center")) {
            "left" -> container.gravity = Gravity.LEFT
            "right" -> container.gravity = Gravity.RIGHT
            else -> container.gravity = Gravity.CENTER
        }

        val circularProgressBarSize = sharedPreferences.getInt("circularProgressBarSize", 70)
        circularProgressBar.indicatorSize = (circularProgressBarSize*0.86).roundToInt() + 18

        circularProgressBar.trackThickness = (circularProgressBarSize * 0.04).roundToInt() + 10

        val paddingTop = (sharedPreferences.getInt("circularProgressBarMarginTop", 70)*0.17).roundToInt() + 13
        val paddingLeft = (sharedPreferences.getInt("circularProgressBarMarginLeft", 70)*0.5).roundToInt() + 15
        val paddingRight = (sharedPreferences.getInt("circularProgressBarMarginRight", 70)*0.5).roundToInt() + 15

        val param = circularProgressBar.layoutParams as LinearLayout.LayoutParams
        param.setMargins(paddingLeft, paddingTop, paddingRight, 0)
        circularProgressBar.layoutParams = param
    }

    private fun linearProgressBarCustomizations(sharedPreferences: SharedPreferences) {
        val progressBarHeight = sharedPreferences.getInt("progressBarHeight", 5)
        progressBar.trackThickness = progressBarHeight + 5

        val paddingTop = sharedPreferences.getInt("linearProgressBarMarginTop", 0)*3
        val param = progressBar.layoutParams as LinearLayout.LayoutParams
        param.setMargins(0, paddingTop, 0, 0)
        progressBar.layoutParams = param
    }

    private fun applyCommonProgressBarCustomizations(sharedPreferences: SharedPreferences) {
        var progressBarColor = sharedPreferences.getInt("progressBarColor", ContextCompat.getColor(this,
            R.color.purple_500
        ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val progressBarColorsArray = resources.getIntArray(R.array.colorsArrayValues)
            if (!progressBarColorsArray.contains(progressBarColor)) {
                progressBarColor = ContextCompat.getColor(this, R.color.system_accent_color)
                sharedPreferences.edit().putInt("progressBarColor", progressBarColor).apply()
            }
        }
        progressBar.setIndicatorColor(progressBarColor)
        circularProgressBar.setIndicatorColor(progressBarColor)

        val blackBackground = sharedPreferences.getBoolean("blackBackground", true)
        val backgroundColor = if (blackBackground) {
            ContextCompat.getColor(this, android.R.color.black)
        } else {
            ContextCompat.getColor(this, android.R.color.transparent)
        }
        progressBar.trackColor = backgroundColor
        circularProgressBar.trackColor = backgroundColor

        val useRoundedCorners = sharedPreferences.getBoolean("useRoundedCorners", false)
        if (useRoundedCorners) {
            progressBar.trackCornerRadius = 100
            circularProgressBar.trackCornerRadius = 100
        } else {
            progressBar.trackCornerRadius = 0
            circularProgressBar.trackCornerRadius = 0
        }
    }

    private fun isInPortraitMode(): Boolean {
        return resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    }

    private fun isLocked(): Boolean {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isKeyguardLocked
    }

    private fun setProgressToZero() {
        progressBar.progress = 0
        circularProgressBar.progress = 0
    }

    private fun hideProgressBarIn(delay: Long) {
        handler.postDelayed({
            progressBar.hide()
            circularProgressBar.hide()
            handler.postDelayed({
                setProgressToZero()
                hideOverlay()
                toBeRemoved = false
            }, 250)
        }, delay)
    }

    private fun animateProgressBarTo(progress: Int, animateCircularProgressBar: Boolean = false) {
        handler.removeCallbacksAndMessages(null)

        if (animateCircularProgressBar) {
            circularProgressBar.show()
            val circularProgressAnimation = ObjectAnimator.ofInt(circularProgressBar, "progress", progress)
            circularProgressAnimation.duration = 250
            circularProgressAnimation.interpolator = DecelerateInterpolator()
            circularProgressAnimation.start()
            progressBar.progress = progress
        } else {
            progressBar.show()
            val progressAnimation = ObjectAnimator.ofInt(progressBar, "progress", progress)
            progressAnimation.duration = 250
            progressAnimation.interpolator = DecelerateInterpolator()
            progressAnimation.start()
            circularProgressBar.progress = progress
        }
    }

    private fun inflateOverlay(showBelowNotch: Boolean = false) {
        if (!this::overlayView.isInitialized) {
            overlayView = View.inflate(this, R.layout.progress_bar, null)
        }

        val params: WindowManager.LayoutParams
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
        } else {
            @Suppress("DEPRECATION")
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
        }
        params.gravity = Gravity.TOP

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!showBelowNotch) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
        }

        if (!overlayView.isShown) {
            getSystemService(WINDOW_SERVICE)?.let {
                (it as WindowManager).addView(overlayView, params)
            }
        }
    }

    private fun hideOverlay() {
        if (this::overlayView.isInitialized && overlayView.isShown) {
            getSystemService(WINDOW_SERVICE)?.let {
                (it as WindowManager).removeView(overlayView)
            }
        }
    }
}