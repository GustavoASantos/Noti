package com.gustavoas.noti.services

import android.accessibilityservice.AccessibilityService
import android.animation.ObjectAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.hardware.display.DisplayManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.gustavoas.noti.R
import com.gustavoas.noti.Utils.getScreenLargeSide
import com.gustavoas.noti.Utils.getScreenSmallSide
import com.gustavoas.noti.Utils.getStatusBarHeight
import com.gustavoas.noti.Utils.hasAccessibilityPermission
import com.gustavoas.noti.Utils.hasSystemAlertWindowPermission
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AccessibilityService : AccessibilityService() {
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val displayManager by lazy { DisplayManagerCompat.getInstance(this) }
    private val keyguardManager by lazy { getSystemService(KEYGUARD_SERVICE) as KeyguardManager }

    private lateinit var overlayView: View
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var circularProgressBar: CircularProgressIndicator
    private val handler = Handler(Looper.getMainLooper())
    private var toBeRemoved = false
    private var currentPriority = 0
    private var currentProgress = 0
    private var currentPackageName = ""
    private var color = 1

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasAccessibilityPermission(this) && !hasSystemAlertWindowPermission(this)) {
            return super.onStartCommand(intent, flags, startId)
        }

        val removal = intent?.getBooleanExtra("removal", false) ?: false
        val newPackageName = intent?.getStringExtra("packageName") ?: ""

        val showInLockScreen = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("showInLockScreen", true)

        if ((removal && newPackageName == currentPackageName) || (isLocked() && !showInLockScreen)) {
            if (!this::overlayView.isInitialized || !overlayView.isShown) {
                return super.onStartCommand(intent, flags, startId)
            }
            currentPriority = 0
            toBeRemoved = true
            hideProgressBarIn(1000)
        } else if ((!isLocked() || showInLockScreen) && !removal) {
            val newProgress = intent?.getIntExtra("progress", 0) ?: 0
            val newPriority = intent?.getIntExtra("priority", 0) ?: 0

            if (newPriority < currentPriority || newProgress <= 0) {
                return super.onStartCommand(intent, flags, startId)
            }

            if (currentPackageName.isNotEmpty() && newPackageName != currentPackageName &&
                newProgress < currentProgress && !toBeRemoved && newPriority == currentPriority) {
                return super.onStartCommand(intent, flags, startId)
            }

            currentPackageName = newPackageName
            currentPriority = newPriority
            color = intent?.getIntExtra("color", 1) ?: 1

            handler.removeCallbacksAndMessages(null)
            showOverlayWithProgress(newProgress)
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun showOverlayWithProgress(progress: Int) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        val progressBarStyle =
            if (sharedPreferences.getBoolean(getSizeDependentPreferenceKey("advancedProgressBarStyle").first, false)) {
                sharedPreferences.getString(
                    if (isInPortraitMode()) {
                        getSizeDependentPreferenceKey("progressBarStylePortrait").first
                    } else {
                        getSizeDependentPreferenceKey("progressBarStyleLandscape").first
                    }, "linear"
                )
            } else {
                sharedPreferences.getString(getSizeDependentPreferenceKey("progressBarStyle").first, "linear")
            }

        if(progressBarStyle == "none") {
            if (this::overlayView.isInitialized && overlayView.isShown) {
                hideProgressBarIn(0)
            }
            return
        }

        if (!this::overlayView.isInitialized || !overlayView.isShown) {
            inflateOverlay()
        }

        progressBar = overlayView.findViewById(R.id.progressBar)
        circularProgressBar = overlayView.findViewById(R.id.circularProgressBar)


        if (progressBarStyle == "circular") {
            progressBar.visibility = View.GONE
            circularProgressBar.visibility = View.VISIBLE

            circularProgressBarCustomizations(sharedPreferences)
        } else {
            progressBar.visibility = View.VISIBLE
            circularProgressBar.visibility = View.GONE

            linearProgressBarCustomizations(sharedPreferences)
        }

        applyCommonProgressBarCustomizations(sharedPreferences)

        animateProgressBarTo(progress, progressBarStyle == "circular")
        currentProgress = progress

        toBeRemoved = progress == resources.getInteger(R.integer.progress_bar_max)

        handler.postDelayed({
            currentPriority = 0
            toBeRemoved = true
        }, 2000)
        hideProgressBarIn(10000)
    }

    private fun circularProgressBarCustomizations(sharedPreferences: SharedPreferences) {
        val trackThickness = sharedPreferences.getSizeDependentInt("circularProgressBarThickness", 15)

        circularProgressBar.indicatorSize = sharedPreferences.getSizeDependentInt("circularProgressBarSize", 65) + 2 * trackThickness

        circularProgressBar.trackThickness = trackThickness

        val marginTop = sharedPreferences.getSizeDependentInt("circularProgressBarMarginTop", 30) - trackThickness
        val horizontalOffset = sharedPreferences.getSizeDependentInt("circularProgressBarHorizontalOffset", 0)

        val overlayParams = overlayView.layoutParams as WindowManager.LayoutParams

        overlayParams.width = circularProgressBar.indicatorSize
        overlayParams.height = circularProgressBar.indicatorSize

        val displayRotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY)!!.rotation

        circularProgressBar.rotation = (360 - displayRotation.toFloat() * 90) % 360

        val progressParams = circularProgressBar.layoutParams as FrameLayout.LayoutParams

        var paddingTop = 0
        var paddingRight = 0
        var paddingLeft = 0

        if (marginTop < 0) {
            paddingTop = marginTop
        } else if (marginTop + circularProgressBar.indicatorSize > getScreenLargeSide(this))  {
            paddingTop = marginTop + circularProgressBar.indicatorSize - getScreenLargeSide(this)
        }

        val halfWidth = getScreenSmallSide(this) / 2
        val halfIndicatorSize = circularProgressBar.indicatorSize / 2

        if (abs(horizontalOffset) + halfIndicatorSize > halfWidth) {
            if (horizontalOffset - halfIndicatorSize < -halfWidth) {
                paddingLeft = halfWidth + horizontalOffset - halfIndicatorSize
            } else {
                paddingLeft = -halfWidth + horizontalOffset + halfIndicatorSize
            }

            if (horizontalOffset + halfIndicatorSize > halfWidth) {
                paddingRight = halfWidth - horizontalOffset - halfIndicatorSize
            } else {
                paddingRight = -halfWidth - horizontalOffset + halfIndicatorSize
            }
        }

        when(displayRotation) {
            Surface.ROTATION_0 -> {
                overlayParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                overlayParams.x = horizontalOffset
                overlayParams.y = marginTop
                progressParams.setMargins(paddingLeft, paddingTop, -paddingLeft, -paddingTop)
            }
            Surface.ROTATION_180 -> {
                overlayParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                overlayParams.x = -horizontalOffset
                overlayParams.y = marginTop
                progressParams.setMargins(paddingRight, -paddingTop, -paddingRight, paddingTop)
            }
            Surface.ROTATION_90 -> {
                // landscape, device top is on the left
                overlayParams.gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
                overlayParams.x = marginTop
                overlayParams.y = -horizontalOffset
                progressParams.setMargins(paddingTop, paddingRight, -paddingTop, -paddingRight)
            }
            Surface.ROTATION_270 -> {
                // landscape, device top is on the right
                overlayParams.gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
                overlayParams.x = marginTop
                overlayParams.y = horizontalOffset
                progressParams.setMargins(-paddingTop, paddingLeft, paddingTop, -paddingLeft)
            }
        }

        circularProgressBar.layoutParams = progressParams

        try {
            windowManager.updateViewLayout(overlayView, overlayParams)
        } catch (_: Exception) {}
    }

    private fun linearProgressBarCustomizations(sharedPreferences: SharedPreferences) {
        if (sharedPreferences.getBoolean(getSizeDependentPreferenceKey("matchStatusBarHeight").first, false)) {
            progressBar.trackThickness = getStatusBarHeight(this)
        } else {
            progressBar.trackThickness = sharedPreferences.getSizeDependentInt("linearProgressBarSize", 15)
        }

        val paddingTop = sharedPreferences.getSizeDependentInt("linearProgressBarMarginTop", 0)

        val overlayParams = overlayView.layoutParams as WindowManager.LayoutParams

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (sharedPreferences.getBoolean(getSizeDependentPreferenceKey("showBelowNotch").first, false)) {
                overlayParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            } else {
                overlayParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        overlayParams.gravity = Gravity.TOP
        overlayParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        overlayParams.height = progressBar.trackThickness + paddingTop
        overlayParams.y = paddingTop

        try {
            windowManager.updateViewLayout(overlayView, overlayParams)
        } catch (_: Exception) {}
    }

    private fun applyCommonProgressBarCustomizations(sharedPreferences: SharedPreferences) {
        val useMaterialYou = sharedPreferences.getBoolean("usingMaterialYouColor", false)
        val progressBarColor = if (color == 1 && !useMaterialYou) {
            sharedPreferences.getInt(
                "progressBarColor", ContextCompat.getColor(this, R.color.purple_500)
            )
        } else if (color == 2 || (useMaterialYou && color == 1)) {
            ContextCompat.getColor(this, R.color.system_accent_color)
        } else {
            color
        }
        progressBar.setIndicatorColor(progressBarColor)
        circularProgressBar.setIndicatorColor(progressBarColor)

        val blackBackground = sharedPreferences.getBoolean("blackBackground", false)
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

    private fun SharedPreferences.getSizeDependentInt(key: String, defaultValue: Int): Int {
        val sizeDependentPreference = getSizeDependentPreferenceKey(key)
        return this.getInt(sizeDependentPreference.first, defaultValue).times(sizeDependentPreference.second).roundToInt()
    }

    private fun getSizeDependentPreferenceKey(key: String): Pair<String, Float> {
        val width = getScreenSmallSide(this)
        val height = getScreenLargeSide(this)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        val relatedPrefs = sharedPreferences.all.filterKeys { it.startsWith(key) }

        val sameRatio = relatedPrefs.filterKeys { it.length > key.length && it[key.length].isDigit() && it.drop(key.length).split("x")[0].toInt() / it.drop(key.length).split("x")[1].toInt() == height / width }

        if (sameRatio.isEmpty()) {
            return Pair(key, 1f)
        }

        val closest = sameRatio.keys.minByOrNull { abs(it.drop(key.length).split("x")[0].toInt() * it.drop(key.length).split("x")[1].toInt() - height * width) }

        val reductionRatio = sqrt((height * width).div(closest!!.drop(key.length).split("x")[0].toFloat() * closest.drop(key.length).split("x")[1].toFloat()))

        return Pair(closest, reductionRatio)
    }

    private fun isInPortraitMode(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    private fun isLocked(): Boolean {
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
                currentPriority = 0
                currentPackageName = ""
                hideOverlay()
                toBeRemoved = false
            }, 500)
        }, delay)
    }

    private fun animateProgressBarTo(progress: Int, animateCircularProgressBar: Boolean = false) {
        if (animateCircularProgressBar) {
            circularProgressBar.show()
            val circularProgressAnimation =
                ObjectAnimator.ofInt(circularProgressBar, "progress", progress)
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

    private fun inflateOverlay() {
        if (!this::overlayView.isInitialized) {
            overlayView = View.inflate(this, R.layout.progress_bar, null)
        }

        val hasSAWPermission = hasSystemAlertWindowPermission(this)
        val hasAccessibilityPermission = hasAccessibilityPermission(this)

        val showInLockscreen = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("showInLockScreen", true) && hasAccessibilityPermission

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasSAWPermission && !showInLockscreen) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasAccessibilityPermission) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && hasSAWPermission) {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        } else {
            return
        }

        val params = WindowManager.LayoutParams (
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
            PixelFormat.TRANSLUCENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasSAWPermission && !showInLockscreen) {
            params.alpha = 0.8f
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (!overlayView.isShown) {
            try {
                windowManager.addView(overlayView, params)
            } catch (_: Exception) {
                // TODO
                return
            }

            if (hasSAWPermission) {
                overlayView.alpha = 0f
                LocalBroadcastManager.getInstance(this).registerReceiver(
                    fullscreenDetectionReceiver,
                    IntentFilter("fullscreenDetectionService")
                )
                startService(Intent(this, FullscreenDetectionService::class.java))
            }

        }
    }

    private val fullscreenDetectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val disableInFullscreen = PreferenceManager.getDefaultSharedPreferences(this@AccessibilityService)
                .getBoolean("disableInFullScreen", true)
            val isFullscreen = intent?.getBooleanExtra("isFullscreen", false) ?: false
            val alpha = if (disableInFullscreen && isFullscreen) 0f else 1f

            overlayView.animate()
                .alpha(alpha)
                .setDuration(300)
                .start()
        }
    }

    private fun hideOverlay() {
        if (this::overlayView.isInitialized && overlayView.isShown) {
            windowManager.removeView(overlayView)
        }
        stopService(Intent(this, FullscreenDetectionService::class.java))
        LocalBroadcastManager.getInstance(this).unregisterReceiver(fullscreenDetectionReceiver)
        stopSelf()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (this::overlayView.isInitialized && overlayView.isShown) {
            if (toBeRemoved) {
                hideProgressBarIn(0)
            } else {
                showOverlayWithProgress(currentProgress)
            }
        }
    }
}