package com.gustavoas.noti.notifications

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import androidx.core.graphics.createBitmap
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import com.gustavoas.noti.ProgressBarAppsRepository
import com.gustavoas.noti.R
import com.gustavoas.noti.Utils.getApplicationIcon
import com.gustavoas.noti.model.ProgressBarApp
import com.gustavoas.noti.model.ProgressNotification
import com.gustavoas.noti.services.AccessibilityService
import kotlin.math.roundToInt

abstract class ProgressBarNotification(
    protected val ctx: Context,
    private var sbn: StatusBarNotification,
    private val appsRepository: ProgressBarAppsRepository
) {
    protected abstract val priorityLevel: Int

    protected var notificationColor = sbn.notification.color

    private val removalHandler = Handler(Looper.getMainLooper())

    open fun updateNotification(sbn: StatusBarNotification) {
        this.sbn = sbn
    }

    open fun cancel() {
        removalHandler.removeCallbacksAndMessages(null)
        sendRemovalRequestToAccessibilityService()
    }

    protected fun sendProgressToAccessibilityService(
        progress: Int = 0,
        progressMax: Int = 0
    ) {
        if (progressMax <= 0 || progress !in 0..progressMax) {
            return
        }

        val appInDatabase = getOrCreateAppInDatabase()
        updateProgressBarColor(appInDatabase)
        if (!appInDatabase.showProgressBar) {
            cancel()
            return
        }

        val progressBarMax = ctx.resources.getInteger(R.integer.progress_bar_max)
        val progressNormalized = if (progress == 0) {
            0
        } else {
            (progress.toFloat() / progressMax.toFloat() * progressBarMax).roundToInt()
        }

        val intent = Intent(ctx, AccessibilityService::class.java)
        intent.putExtra("id", sbn.key ?: "")
        intent.putExtra(
            "progressNotification",
            ProgressNotification(
                appInDatabase,
                progressNormalized,
                priorityLevel
            )
        )
        ctx.startService(intent)

        removalHandler.removeCallbacksAndMessages(null)

        removalHandler.postDelayed({
            sendRemovalRequestToAccessibilityService()
        }, 10000)
    }

    private fun sendRemovalRequestToAccessibilityService() {
        val intent = Intent(ctx, AccessibilityService::class.java)
        intent.putExtra("id", sbn.key ?: "")
        intent.putExtra("removal", true)
        ctx.startService(intent)
    }

    private fun updateProgressBarColor(progressBarApp: ProgressBarApp) {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val useNotificationColor = sharedPrefs.getBoolean("useNotificationColor", false)

        if (!progressBarApp.useDefaultColor || !useNotificationColor) {
            return
        }

        if (notificationColor == Notification.COLOR_DEFAULT) {
            val appIcon = getApplicationIcon(ctx, sbn.packageName)

            appIcon?.let {
                getColorFromBitmap(drawableToBitmap(it))?.let { color ->
                    notificationColor = color
                }
            }
        }

        if (notificationColor != Notification.COLOR_DEFAULT && progressBarApp.color != notificationColor) {
            progressBarApp.color = notificationColor
            appsRepository.updateApp(progressBarApp)
        }
    }

    protected fun getColorFromBitmap(bitmap: Bitmap): Int? {
        val palette = Palette.from(bitmap).generate()
        val swatch = palette.lightMutedSwatch
            ?: palette.vibrantSwatch
            ?: palette.dominantSwatch

        return swatch?.rgb
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap

        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

    private fun getOrCreateAppInDatabase(): ProgressBarApp {
        return appsRepository.let {
            it.getApp(sbn.packageName ?: "") ?: it.addApp(ProgressBarApp(sbn.packageName, true))
        }
    }
}