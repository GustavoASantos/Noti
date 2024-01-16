package com.gustavoas.noti.services

import android.accessibilityservice.AccessibilityService
import android.animation.ObjectAnimator
import android.app.KeyguardManager
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.gustavoas.noti.R
import com.gustavoas.noti.Utils.hasAccessibilityPermission
import com.gustavoas.noti.Utils.hasSystemAlertWindowPermission
import kotlin.math.roundToInt

class AccessibilityService : AccessibilityService() {
    private lateinit var overlayView: View
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var circularProgressBar: CircularProgressIndicator
    private val handler = Handler(Looper.getMainLooper())
    private var toBeRemoved = false
    private var previousWasLowPriority = false
    private var lowPriorityTimeout = false
    private var lowPriorityHandler = Handler(Looper.getMainLooper())
    private var color = 1

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasAccessibilityPermission(this) && !hasSystemAlertWindowPermission(this)) {
            return super.onStartCommand(intent, flags, startId)
        }

        val progress = intent?.getIntExtra("progress", 0) ?: 0
        val progressMax = intent?.getIntExtra("progressMax", 0) ?: 0
        val removal = intent?.getBooleanExtra("removal", false) ?: false

        val showInLockScreen = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("showInLockScreen", true)

        if (removal || (isLocked() && !showInLockScreen)) {
            if (!this::overlayView.isInitialized || !overlayView.isShown) {
                return super.onStartCommand(intent, flags, startId)
            }
            lowPriorityTimeout = false
            toBeRemoved = true
            hideProgressBarIn(1000)
        } else if ((!isLocked() || showInLockScreen) && progress > 0) {
            val lowPriority = intent?.getBooleanExtra("lowPriority", false) ?: false
            if (lowPriority && lowPriorityTimeout) {
                return super.onStartCommand(intent, flags, startId)
            } else if (!lowPriority) {
                lowPriorityTimeout = true
                lowPriorityHandler.removeCallbacksAndMessages(null)
                lowPriorityHandler.postDelayed({
                    lowPriorityTimeout = false
                }, 2500)
            }

            color = intent?.getIntExtra("color", 1) ?: 1
            showOverlayWithProgress(progress, progressMax)
            previousWasLowPriority = lowPriority
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun showOverlayWithProgress(progress: Int, progressMax: Int) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        val disableInLandscape = sharedPreferences.getBoolean("disableInLandscape", false)
        if (disableInLandscape && !isInPortraitMode()) {
            if (this::overlayView.isInitialized && overlayView.isShown) {
                hideProgressBarIn(0)
            }
            return
        }

        var useCircularProgressBar = (sharedPreferences.getString(
                "progressBarStyle", "linear"
            ) == "circular" && isInPortraitMode())

        useCircularProgressBar = true; // TODO: always use circular for testing

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

        val progressBarMax = progressBar.max

        val currentProgress =
            (progress.toDouble() / progressMax.toDouble() * progressBarMax).roundToInt()

        if (currentProgress < progressBar.progress && !toBeRemoved && !previousWasLowPriority) {
            return
        }

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

        animateProgressBarTo(currentProgress, useCircularProgressBar)

        toBeRemoved = currentProgress == progressBarMax

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

        // add navbar height to container margins
        val navBarHeight = resources.getDimensionPixelSize(
            resources.getIdentifier("navigation_bar_height", "dimen", "android")
        )
        containerParams.setMargins(0, 0, 0, -navBarHeight)

        // apply layout depending on absolute screen rotation and location
        val progressBarLocation = sharedPreferences.getString("progressBarLocation", "center")
        val displayManager = DisplayManagerCompat.getInstance(this)
        when(displayManager.getDisplay(Display.DEFAULT_DISPLAY)!!.rotation) {
            Surface.ROTATION_0 -> {
                // portrait
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
                // TODO: not tested, as most devices do not support this
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
                progressParams.setMargins(0, paddingRight, paddingTop, paddingLeft)
            }
        }

        // apply layout
        container.layoutParams = containerParams
        circularProgressBar.layoutParams = progressParams
    }

    private fun linearProgressBarCustomizations(sharedPreferences: SharedPreferences) {
        val progressBarHeight = sharedPreferences.getInt("progressBarHeight", 5)
        val statusBarHeight = resources.getDimensionPixelSize(
            resources.getIdentifier("status_bar_height", "dimen", "android")
        )
        if ((statusBarHeight > 15 && progressBarHeight == 10) || progressBarHeight == statusBarHeight - 5) {
            progressBar.trackThickness = statusBarHeight
        } else {
            progressBar.trackThickness = progressBarHeight + 5
        }

        val paddingTop = sharedPreferences.getInt("linearProgressBarMarginTop", 0) * 3
        val param = progressBar.layoutParams as LinearLayout.LayoutParams
        param.setMargins(0, paddingTop, 0, 0)
        progressBar.layoutParams = param
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
            }, 500)
        }, delay)
    }

    private fun animateProgressBarTo(progress: Int, animateCircularProgressBar: Boolean = false) {
        handler.removeCallbacksAndMessages(null)

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

        val showInLockscreen = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("showInLockScreen", true) && hasAccessibilityPermission(this)

        val params: WindowManager.LayoutParams
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasSystemAlertWindowPermission(this) && !showInLockscreen) {
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasAccessibilityPermission(this)) {
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && hasSystemAlertWindowPermission(this)) {
            @Suppress("DEPRECATION")
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
        } else {
            return
        }
        params.gravity = Gravity.TOP

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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (this::overlayView.isInitialized && overlayView.isShown) {
            if (toBeRemoved) {
                hideProgressBarIn(0)
            } else {
                showOverlayWithProgress(
                    maxOf(progressBar.progress, circularProgressBar.progress), progressBar.max
                )
            }
        }
    }
}