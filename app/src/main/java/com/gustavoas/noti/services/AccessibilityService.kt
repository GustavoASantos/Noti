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
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.hardware.display.DisplayManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.gustavoas.noti.R
import com.gustavoas.noti.Utils.hasAccessibilityPermission
import com.gustavoas.noti.Utils.hasSystemAlertWindowPermission
import kotlin.math.roundToInt

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
            if (sharedPreferences.getBoolean("advancedProgressBarStyle", false)) {
                sharedPreferences.getString(
                    if (isInPortraitMode()) {
                        "progressBarStylePortrait"
                    } else {
                        "progressBarStyleLandscape"
                    }, "linear"
                )
            } else {
                sharedPreferences.getString("progressBarStyle", "linear")
            }

        if(progressBarStyle == "none") {
            if (this::overlayView.isInitialized && overlayView.isShown) {
                hideProgressBarIn(0)
            }
            return
        }

        if (!this::overlayView.isInitialized || !overlayView.isShown) {
            if (progressBarStyle == "linear") {
                val showBelowNotch = sharedPreferences.getBoolean("showBelowNotch", false)
                inflateOverlay(showBelowNotch)
            } else {
                inflateOverlay()
            }
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
        val container = overlayView.findViewById<FrameLayout>(R.id.container)

        val circularProgressBarSize = sharedPreferences.getInt("circularProgressBarSize", 70)
        circularProgressBar.indicatorSize = (circularProgressBarSize * 0.86).roundToInt() + 18

        circularProgressBar.trackThickness = (circularProgressBarSize * 0.04).roundToInt() + 10

        val paddingTop =
            (sharedPreferences.getInt("circularProgressBarMarginTop", 70) * 0.17).roundToInt() + 13
        val paddingLeft =
            (sharedPreferences.getInt("circularProgressBarMarginLeft", 70) * 0.5).roundToInt() + 15
        val paddingRight =
            (sharedPreferences.getInt("circularProgressBarMarginRight", 70) * 0.5).roundToInt() + 15

        // get layout params
        val containerParams = container.layoutParams as ConstraintLayout.LayoutParams
        val progressParams = circularProgressBar.layoutParams as FrameLayout.LayoutParams

        // reset container constraints
        containerParams.topToTop = -1
        containerParams.bottomToBottom = -1
        containerParams.leftToLeft = -1
        containerParams.rightToRight = -1

        // apply layout depending on absolute screen rotation and location
        val progressBarLocation = sharedPreferences.getString("progressBarLocation", "center")
        when(displayManager.getDisplay(Display.DEFAULT_DISPLAY)!!.rotation) {
            Surface.ROTATION_0 -> {
                // portrait
                circularProgressBar.rotation = 0f
                // align to top
                containerParams.topToTop = R.id.container_parent
                when (progressBarLocation) {
                    "left" -> {
                        // align to left
                        containerParams.leftToLeft = R.id.container_parent
                    }
                    "right" -> {
                        // align to right
                        containerParams.rightToRight = R.id.container_parent
                    }
                    else -> {
                        // align to center
                        containerParams.leftToLeft = R.id.container_parent
                        containerParams.rightToRight = R.id.container_parent
                    }
                }

                // set margins normally
                progressParams.setMargins(paddingLeft, paddingTop, paddingRight, 0)
            }
            Surface.ROTATION_180 -> {
                // inverted portrait
                circularProgressBar.rotation = 180f
                // align to bottom
                containerParams.bottomToBottom = R.id.container_parent
                when (progressBarLocation) {
                    "left" -> {
                        // align to right
                        containerParams.rightToRight = R.id.container_parent
                    }
                    "right" -> {
                        // align to left
                        containerParams.leftToLeft = R.id.container_parent
                    }
                    else -> {
                        // align to center
                        containerParams.rightToRight = R.id.container_parent
                        containerParams.leftToLeft = R.id.container_parent
                    }
                }

                // set margins rotated by 180°
                progressParams.setMargins(paddingRight, 0, paddingLeft, paddingTop)
            }
            Surface.ROTATION_90 -> {
                // landscape, device top is on the left
                circularProgressBar.rotation = 270f
                // align to left
                containerParams.leftToLeft = R.id.container_parent
                when (progressBarLocation) {
                    "left" -> {
                        // align to bottom
                        containerParams.bottomToBottom = R.id.container_parent
                    }
                    "right" -> {
                        // align to top
                        containerParams.topToTop = R.id.container_parent
                    }
                    else -> {
                        // align to center
                        containerParams.bottomToBottom = R.id.container_parent
                        containerParams.topToTop = R.id.container_parent
                    }
                }

                // set margins rotated by 90°
                progressParams.setMargins(paddingTop, paddingRight, 0, paddingLeft)
            }
            Surface.ROTATION_270 -> {
                // landscape, device top is on the right
                circularProgressBar.rotation = 90f
                // align to right
                containerParams.rightToRight = R.id.container_parent
                when (progressBarLocation) {
                    "left" -> {
                        // align to top
                        containerParams.topToTop = R.id.container_parent
                    }

                    "right" -> {
                        // align to bottom
                        containerParams.bottomToBottom = R.id.container_parent
                    }

                    else -> {
                        // align to center
                        containerParams.topToTop = R.id.container_parent
                        containerParams.bottomToBottom = R.id.container_parent
                    }
                }

                // set margins rotated by -90°
                progressParams.setMargins(0, paddingLeft, paddingTop, paddingRight)
            }
        }

        // apply layout
        container.layoutParams = containerParams
        circularProgressBar.layoutParams = progressParams
    }

    private fun linearProgressBarCustomizations(sharedPreferences: SharedPreferences) {
        val progressBarHeight = sharedPreferences.getInt("progressBarHeight", 5)
        if ((statusBarHeight > 15 && progressBarHeight == 10) || progressBarHeight == statusBarHeight - 5) {
            progressBar.trackThickness = statusBarHeight
        } else {
            progressBar.trackThickness = progressBarHeight + 5
        }

        val paddingTop = sharedPreferences.getInt("linearProgressBarMarginTop", 0) * 3
        val param = progressBar.layoutParams as FrameLayout.LayoutParams
        param.setMargins(0, paddingTop, 0, 0)
        progressBar.layoutParams = param

        val container = overlayView.findViewById<FrameLayout>(R.id.container)
        val containerParams = container.layoutParams as ConstraintLayout.LayoutParams

        // set container constraints
        containerParams.topToTop = R.id.container_parent
        containerParams.bottomToBottom = -1
        containerParams.leftToLeft = -1
        containerParams.rightToRight = -1
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

    private fun isInPortraitMode(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    private fun isLocked(): Boolean {
        return keyguardManager.isKeyguardLocked
    }

    private val statusBarHeight: Int
        get() {
            return resources.getDimensionPixelSize(
                resources.getIdentifier("status_bar_height", "dimen", "android")
            )
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

    private fun inflateOverlay(showBelowNotch: Boolean = false) {
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
            if (!showBelowNotch) {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
        }

        if (!overlayView.isShown) {
            try {
                windowManager.addView(overlayView, params)
            } catch (e: WindowManager.BadTokenException) {
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
            val alpha = if (disableInFullscreen) {
                intent?.getFloatExtra("alpha", 1f) ?: 1f
            } else {
                1f
            }

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