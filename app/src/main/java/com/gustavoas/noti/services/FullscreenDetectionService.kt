package com.gustavoas.noti.services

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.gustavoas.noti.R
import com.gustavoas.noti.Utils.getRealDisplayHeight
import com.gustavoas.noti.Utils.hasSystemAlertWindowPermission

class FullscreenDetectionService : Service() {
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private lateinit var fullscreenDetectionView: View
    private lateinit var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasSystemAlertWindowPermission(this)) {
            return super.onStartCommand(intent, flags, startId)
        }

        if (!this::fullscreenDetectionView.isInitialized || !fullscreenDetectionView.isShown) {
            inflateFullscreenDetectionOverlay()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun inflateFullscreenDetectionOverlay() {
        if (!this::fullscreenDetectionView.isInitialized) {
            fullscreenDetectionView = View.inflate(this, R.layout.progress_bar, null)
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        }

        val fullscreenDetectorParams = WindowManager.LayoutParams(
            0,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        fullscreenDetectorParams.alpha = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            fullscreenDetectorParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        windowManager.addView(fullscreenDetectionView, fullscreenDetectorParams)

        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val displayHeight = getRealDisplayHeight(this)

            val alpha = if (fullscreenDetectionView.height == displayHeight) {
                0f
            } else {
                1f
            }

            val intent = Intent("fullscreenDetectionService")
            intent.putExtra("alpha", alpha)

            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }

        fullscreenDetectionView.viewTreeObserver.addOnGlobalLayoutListener(
            globalLayoutListener
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::fullscreenDetectionView.isInitialized && fullscreenDetectionView.isShown) {
            windowManager.removeView(fullscreenDetectionView)
            if (fullscreenDetectionView.viewTreeObserver.isAlive) {
                fullscreenDetectionView.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
            }
        }
    }
}