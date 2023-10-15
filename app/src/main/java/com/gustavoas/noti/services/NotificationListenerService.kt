package com.gustavoas.noti.services

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.gustavoas.noti.ProgressBarAppsRepository
import com.gustavoas.noti.model.ProgressBarApp

class NotificationListenerService : NotificationListenerService() {
    private val appsRepository by lazy { ProgressBarAppsRepository.getInstance(this) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        if (hasProgressBarNotIndeterminate(sbn)) {
            val (progress, progressMax) = getProgressBarValues(sbn)
            val percentageProgress = getProgressFromPercentage(sbn).toInt()

            if (progress > 0 && progressMax > 0) {
                sendProgressToAccessibilityService(progress, progressMax, packageName=sbn.packageName)
            } else if (percentageProgress in 1..100) {
                sendProgressToAccessibilityService(percentageProgress, 100, packageName=sbn.packageName)
            } else {
                sendProgressToAccessibilityService(removal=true)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)

        if (hasProgressBarNotIndeterminate(sbn) || activeNotifications.isEmpty()) {
            sendProgressToAccessibilityService(removal=true)
        }
    }

    private fun hasProgressBarNotIndeterminate(sbn: StatusBarNotification): Boolean {
        val progress = sbn.notification.extras.containsKey("android.progress")
        val progressMax = sbn.notification.extras.containsKey("android.progressMax")
        val isIndeterminate = sbn.notification.extras.getBoolean("android.progressIndeterminate")

        return progress && progressMax && !isIndeterminate
    }

    private fun getProgressBarValues(sbn: StatusBarNotification): Pair<Int, Int> {
        val progress = sbn.notification.extras.getInt("android.progress")
        val progressMax = sbn.notification.extras.getInt("android.progressMax")
        return Pair(progress, progressMax)
    }

    private fun getProgressFromPercentage(sbn: StatusBarNotification): Float {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title").toString()
        val text = extras.getCharSequence("android.text").toString()
        val subText = extras.getCharSequence("android.subText").toString()
        val bigText = extras.getCharSequence("android.bigText").toString()
        val textLines = extras.getCharSequenceArray("android.textLines")

        return title.substringBefore("%").toFloatOrNull() ?:
            text.substringBefore("%").toFloatOrNull() ?:
            subText.substringBefore("%").toFloatOrNull() ?:
            bigText.split("\n").firstOrNull { it.contains("%") }?.toString()
            ?.substringBefore("%")?.toFloatOrNull() ?:
            textLines?.firstOrNull { it.contains("%") }?.toString()
            ?.substringBefore("%")?.toFloatOrNull() ?: 0f
    }

    private fun showForApp(packageName: String): Boolean {
        val appInDatabase = appsRepository.getApp(packageName)
        return if (appInDatabase == null) {
            if (packageName.isNotEmpty())
                appsRepository.addApp(ProgressBarApp(packageName, true))
            true
        } else
            appInDatabase.showProgressBar
    }

    private fun sendProgressToAccessibilityService(progress: Int = 0, progressMax: Int = 0, removal: Boolean = false, packageName: String = "") {
        if (!showForApp(packageName)) {
            return
        }

        val intent = Intent(this, AccessibilityService::class.java)
        intent.putExtra("progress", progress)
        intent.putExtra("progressMax", progressMax)
        intent.putExtra("removal", removal)
        startService(intent)
    }
}