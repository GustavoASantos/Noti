package com.gustavoas.noti.notifications

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.preference.PreferenceManager
import com.gustavoas.noti.ProgressBarAppsRepository

class DownloadProgressBar(
    ctx: Context,
    sbn: StatusBarNotification,
    appsRepository: ProgressBarAppsRepository
): ProgressBarNotification(ctx, sbn, appsRepository) {
    override val priorityLevel: Int = 2

    init {
        updateNotification(sbn)
    }

    override fun updateNotification(sbn: StatusBarNotification) {
        super.updateNotification(sbn)

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val enabledForDownloads = sharedPrefs.getBoolean("showForDownloads", true)

        if (!enabledForDownloads) {
            cancel()
            return
        }

        val (progress, progressMax) = getProgressBarValues(sbn)

        if (progress == 0 && progressMax == 0) {
            cancel()
            return
        }

        sendProgressToAccessibilityService(progress, progressMax)
    }

    private fun getProgressBarValues(sbn: StatusBarNotification): Pair<Int, Int> {
        val progress = sbn.notification.extras.getInt(Notification.EXTRA_PROGRESS)
        val progressMax = sbn.notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX)
        return Pair(progress, progressMax)
    }
}