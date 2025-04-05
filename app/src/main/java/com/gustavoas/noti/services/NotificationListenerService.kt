package com.gustavoas.noti.services

import android.app.Notification
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.gustavoas.noti.ProgressBarAppsRepository
import com.gustavoas.noti.notifications.DownloadProgressBar
import com.gustavoas.noti.notifications.GoogleTimerProgressBar
import com.gustavoas.noti.notifications.MediaProgressBar
import com.gustavoas.noti.notifications.PercentageProgressBar
import com.gustavoas.noti.notifications.ProgressBarNotification
import kotlin.math.roundToInt

class NotificationListenerService : NotificationListenerService() {
    private val appsRepository by lazy { ProgressBarAppsRepository.getInstance(this) }

    private val activeProgressBars = mutableMapOf<String, ProgressBarNotification>()

    private fun isMediaNotification(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras

        val template = extras.getString(Notification.EXTRA_TEMPLATE)
        val templateMatch = Notification.MediaStyle::class.java.name

        if (template != templateMatch) {
            return false
        }

        return sbn.notification.extras.containsKey(NotificationCompat.EXTRA_MEDIA_SESSION)
    }

    private fun isDownloadNotification(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras

        val hasProgress = extras.getInt(NotificationCompat.EXTRA_PROGRESS) > 0
        val hasProgressMax = extras.getInt(NotificationCompat.EXTRA_PROGRESS_MAX) > 0
        val isIndeterminate = extras.getBoolean(NotificationCompat.EXTRA_PROGRESS_INDETERMINATE)

        return hasProgress && hasProgressMax && !isIndeterminate
    }

    // TODO Do this differently
    private fun getProgressFromPercentage(sbn: StatusBarNotification): Int {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)

        val percentageProgress = title.substringBefore("%").toFloatOrNull() ?:
        text.substringBefore("%").toFloatOrNull() ?:
        subText.substringBefore("%").toFloatOrNull() ?:
        bigText.split("\n").firstOrNull { it.contains("%") }?.toString()
            ?.substringBefore("%")?.toFloatOrNull() ?:
        textLines?.firstOrNull { it.contains("%") }?.toString()
            ?.substringBefore("%")?.toFloatOrNull()

        if (percentageProgress == null || percentageProgress.isNaN()) {
            return 0
        }

        return percentageProgress.roundToInt()
    }

    private fun isGoogleTimerNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName != "com.google.android.deskclock") {
            return false
        }

        val sortKey = sbn.notification.sortKey
        return !sortKey.isNullOrEmpty()
    }

    private fun shouldShowProgressForNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == this.packageName) {
            return false
        }

        val uniqueIdentifier = sbn.key ?: return false

        if (activeProgressBars.containsKey(uniqueIdentifier)) {
            return true
        }

        if (!showForApp(sbn.packageName ?: "")) {
            return false
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val enabledForMedia = sharedPreferences.getBoolean("showForMedia", true)
        if (isMediaNotification(sbn) && enabledForMedia) {
            return true
        }

        val enabledForDownloads = sharedPreferences.getBoolean("showForDownloads", true)
        if (isDownloadNotification(sbn) && enabledForDownloads) {
            return true
        }

        if (getProgressFromPercentage(sbn) > 0 && enabledForDownloads) {
            return true
        }

        return isGoogleTimerNotification(sbn)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val notificationUser = sbn.user
        val packageUser = Process.myUserHandle()

        if (notificationUser != packageUser) {
            return
        }

        if (shouldShowProgressForNotification(sbn)) {
            getProgressFromNotification(sbn)
        }
    }

    private fun getProgressFromNotification(sbn: StatusBarNotification) {
        val uniqueIdentifier = sbn.key.toString()

        if (activeProgressBars.containsKey(uniqueIdentifier)) {
            val notification = activeProgressBars[uniqueIdentifier]

            notification?.updateNotification(sbn)

            return
        }

        activeProgressBars[uniqueIdentifier] = if (isMediaNotification(sbn)) {
            MediaProgressBar(this, sbn, appsRepository)
        } else if (isGoogleTimerNotification(sbn)) {
            GoogleTimerProgressBar(this, sbn, appsRepository)
        } else if (isDownloadNotification(sbn)) {
            DownloadProgressBar(this, sbn, appsRepository)
        }  else if (getProgressFromPercentage(sbn) > 0) {
            PercentageProgressBar(this, sbn, appsRepository)
        } else {
            return
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)

        val activeProgressBar = activeProgressBars.remove(sbn.key.toString())
        activeProgressBar?.cancel()
    }

    private fun showForApp(packageName: String): Boolean {
        val appInDatabase = appsRepository.getApp(packageName)

        if (appInDatabase == null && packageName.isNotEmpty()) {
            return true
        }

        return appInDatabase?.showProgressBar == true
    }
}