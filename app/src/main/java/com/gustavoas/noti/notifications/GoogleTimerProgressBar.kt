package com.gustavoas.noti.notifications

import android.content.Context
import android.service.notification.StatusBarNotification
import com.gustavoas.noti.ProgressBarAppsRepository

class GoogleTimerProgressBar(
    ctx: Context,
    sbn: StatusBarNotification,
    appsRepository: ProgressBarAppsRepository
): TimedProgressBar(ctx, sbn, appsRepository) {
    override val priorityLevel: Int = 1

    init {
        updateNotification(sbn)
    }

    override fun updateNotification(sbn: StatusBarNotification) {
        super.updateNotification(sbn)

        val sortKey = sbn.notification.sortKey

        if (!sortKey.contains("RUNNING")) {
            cancel()
            return
        }

        val splitKey = sortKey.split("|")
        val timeLeft = splitKey.firstOrNull { it.contains("⏳") } ?: return
        val totalTime = splitKey.firstOrNull { it.contains("Σ") } ?: return
        val timeLeftMillis = parseTimeStringToMillis(timeLeft.substringAfter("⏳"))
        val totalTimeMillis = parseTimeStringToMillis(totalTime.substringAfter("Σ"))

        startUpdatingTimedPosition(timeLeftMillis, totalTimeMillis, -1f)
    }

    private fun parseTimeStringToMillis(time: String): Long {
        val timeSplit = time.trim().split(":")
        val hours = timeSplit.getOrNull(0)?.toLongOrNull() ?: 0
        val minutes = timeSplit.getOrNull(1)?.toLongOrNull() ?: 0
        val seconds = timeSplit.getOrNull(2)?.toLongOrNull() ?: 0
        return (hours * 3600 + minutes * 60 + seconds) * 1000
    }
}