package com.gustavoas.noti.notifications

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import com.gustavoas.noti.ProgressBarAppsRepository
import com.gustavoas.noti.R
import com.gustavoas.noti.model.ProgressBarApp
import com.gustavoas.noti.model.ProgressNotification
import com.gustavoas.noti.services.AccessibilityService
import kotlin.math.roundToInt

abstract class ProgressBarNotification(
    protected val ctx: Context,
    private var sbn: StatusBarNotification,
    private val appsRepository: ProgressBarAppsRepository
) {
    abstract val priorityLevel: Int

    private val removalHandler = Handler(Looper.getMainLooper())

    open fun updateNotification(sbn: StatusBarNotification) {
        this.sbn = sbn
    }

    open fun cancel() {
        sendRemovalRequestToAccessibilityService()
    }

    protected fun sendProgressToAccessibilityService(
        progress: Int = 0,
        progressMax: Int = 0
    ) {
        removalHandler.removeCallbacksAndMessages(null)

        val appInDatabase = getOrCreateAppInDatabase()

        if (!appInDatabase.showProgressBar) {
            cancel()
            return
        }

        val progressBarMax = ctx.resources.getInteger(R.integer.progress_bar_max)
        val progressNormalized = if (progress in 1..progressMax) {
            (progress.toFloat() / progressMax.toFloat() * progressBarMax).roundToInt()
        } else {
            0
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

    private fun getOrCreateAppInDatabase(): ProgressBarApp {
        return appsRepository.let {
            it.getApp(sbn.packageName ?: "") ?: it.addApp(ProgressBarApp(sbn.packageName, true))
        }
    }
}