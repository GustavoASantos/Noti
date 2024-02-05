package com.gustavoas.noti.services

import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import com.gustavoas.noti.ProgressBarAppsRepository
import com.gustavoas.noti.R
import com.gustavoas.noti.model.ProgressBarApp
import kotlin.math.roundToInt

class NotificationListenerService : NotificationListenerService() {
    private val appsRepository by lazy { ProgressBarAppsRepository.getInstance(this) }
    private var mediaController: MediaController? = null
    private var mediaCallback: MediaController.Callback? = null
    private val mediaHandler = Handler(Looper.getMainLooper())
    private val timerHandler = Handler(Looper.getMainLooper())
    private var mediaPriority = 0
    private var timerPriority = 1
    private var downloadPriority = 2

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val mediaSession =
                sbn.notification.extras.get("android.mediaSession") as? MediaSession.Token
            if (mediaSession != null) {
                val showForMedia = PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean("showForMedia", true)
                if (showForMedia) {
                    val newMediaController = MediaController(this, mediaSession)
                    if (mediaController?.packageName != newMediaController.packageName && newMediaController.playbackState?.state == PlaybackState.STATE_PLAYING) {
                        stopUpdatingMediaPosition()
                        createProgressBarFromMedia(newMediaController)
                    }
                }
                return
            }
        }

        if (sbn.packageName == "com.google.android.deskclock" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            val sortKey = sbn.notification.sortKey
            if (sortKey != null) {
                if (sortKey.contains("RUNNING")) {
                    val splitKey = sortKey.split("|")
                    val elapsedTime = splitKey.firstOrNull { it.contains("⏳") } ?: return
                    val totalTime = splitKey.firstOrNull { it.contains("Σ") } ?: return
                    val elapsedTimeMillis = parseTimeStringToMillis(elapsedTime.substringAfter("⏳"))
                    val totalTimeMillis = parseTimeStringToMillis(totalTime.substringAfter("Σ"))

                    timerHandler.removeCallbacksAndMessages(null)
                    startUpdatingTimedPosition(sbn.packageName, elapsedTimeMillis, totalTimeMillis, -1f, timerPriority, timerHandler)
                } else if (sortKey.contains("PAUSED")) {
                    timerHandler.removeCallbacksAndMessages(null)
                    sendRemovalRequestToAccessibilityService(sbn.packageName)
                }

                return
            }
        }

        val showForDownloads = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("showForDownloads", true)
        if (hasProgressBarNotIndeterminate(sbn) && showForDownloads) {
            val (progress, progressMax) = getProgressBarValues(sbn)
            val percentageProgress = getProgressFromPercentage(sbn)

            if (progress > 0 && progressMax > 0) {
                sendProgressToAccessibilityService(
                    sbn.packageName, progress, progressMax, downloadPriority
                )
            } else if (percentageProgress in 1..100) {
                sendProgressToAccessibilityService(
                    sbn.packageName, percentageProgress, 100, downloadPriority
                )
            } else {
                sendRemovalRequestToAccessibilityService(sbn.packageName)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaController?.packageName == sbn.packageName) {
            stopUpdatingMediaPosition()
        } else if (sbn.packageName == "com.google.android.deskclock" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            timerHandler.removeCallbacksAndMessages(null)
            sendRemovalRequestToAccessibilityService(sbn.packageName)
        } else if (hasProgressBarNotIndeterminate(sbn)) {
            sendRemovalRequestToAccessibilityService(sbn.packageName)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun createProgressBarFromMedia(newMediaController: MediaController) {
        mediaController = newMediaController

        startUpdatingTimedPosition(
            mediaController?.packageName ?: "",
            mediaController?.playbackState?.position ?: 0,
            mediaController?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
            mediaController?.playbackState?.playbackSpeed ?: 1f,
            mediaPriority,
            mediaHandler
        )

        mediaCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                super.onPlaybackStateChanged(state)
                mediaHandler.removeCallbacksAndMessages(null)
                val showForMedia = PreferenceManager.getDefaultSharedPreferences(this@NotificationListenerService)
                    .getBoolean("showForMedia", true)
                if (state?.state == PlaybackState.STATE_PLAYING && showForMedia) {
                    startUpdatingTimedPosition(
                        mediaController?.packageName ?: "",
                        state.position,
                        mediaController?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
                        state.playbackSpeed,
                        mediaPriority,
                        mediaHandler
                    )
                } else {
                    sendRemovalRequestToAccessibilityService(mediaController?.packageName ?: "")
                    mediaHandler.postDelayed({
                        stopUpdatingMediaPosition()
                    }, 1000)
                }
            }
        }

        mediaController?.registerCallback(mediaCallback as MediaController.Callback)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun stopUpdatingMediaPosition() {
        sendRemovalRequestToAccessibilityService(mediaController?.packageName ?: "")
        mediaHandler.removeCallbacksAndMessages(null)
        if (mediaCallback != null) {
            mediaController?.unregisterCallback(mediaCallback as MediaController.Callback)
            mediaCallback = null
        }
        mediaController = null
    }

    private fun parseTimeStringToMillis(time: String): Long {
        val timeSplit = time.trim().split(":")
        val hours = timeSplit[0].toLongOrNull() ?: return 0
        val minutes = timeSplit[1].toLongOrNull() ?: return 0
        val seconds = timeSplit[2].toLongOrNull() ?: return 0
        return (hours * 3600 + minutes * 60 + seconds) * 1000
    }

    private fun startUpdatingTimedPosition(
        packageName: String,
        initialPosition: Long,
        duration: Long,
        speed: Float,
        priorityLevel: Int,
        handler: Handler
    ) {
        var currProgress = initialPosition
        val runnable = object : Runnable {
            override fun run() {
                if (currProgress in 0..duration) {
                    val progressBarMax = resources.getInteger(R.integer.progress_bar_max)
                    val progressNormalized = if (currProgress in 1 .. duration) {
                        (currProgress.toFloat() / duration.toFloat() * progressBarMax).roundToInt()
                    } else {
                        0
                    }
                    sendProgressToAccessibilityService(
                        packageName,
                        progressNormalized,
                        progressBarMax,
                        priorityLevel
                    )
                    val updateInterval = 1000
                    currProgress += (updateInterval * speed).toInt()
                    if (speed > 0 && duration - currProgress in 0..(updateInterval * speed).toInt()) {
                        currProgress = duration
                    } else if (speed < 0 && currProgress in 1 until updateInterval) {
                        currProgress = 1
                    }
                    handler.postDelayed(this, updateInterval.toLong())
                }
            }
        }
        runnable.run()
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

    private fun getProgressFromPercentage(sbn: StatusBarNotification): Int {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val subText = extras.getCharSequence("android.subText")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val textLines = extras.getCharSequenceArray("android.textLines")

        val percentageProgress = title.substringBefore("%").toFloatOrNull() ?:
            text.substringBefore("%").toFloatOrNull() ?:
            subText.substringBefore("%").toFloatOrNull() ?:
            bigText.split("\n").firstOrNull { it.contains("%") }?.toString()
            ?.substringBefore("%")?.toFloatOrNull() ?:
            textLines?.firstOrNull { it.contains("%") }?.toString()
            ?.substringBefore("%")?.toFloatOrNull() ?: 0f

        return percentageProgress.roundToInt()
    }

    private fun showForApp(appInDatabase: ProgressBarApp?, packageName: String): Boolean {
        return if (appInDatabase == null) {
            if (packageName.isNotEmpty()) appsRepository.addApp(ProgressBarApp(packageName, true))
            true
        } else appInDatabase.showProgressBar
    }

    private fun sendProgressToAccessibilityService(
        packageName: String,
        progress: Int = 0,
        progressMax: Int = 0,
        priorityLevel: Int = 0
    ) {
        val appInDatabase = appsRepository.getApp(packageName)
        if (!showForApp(appInDatabase, packageName)) {
            return
        }

        val activeNotifications = try {
            activeNotifications
        } catch (e: Exception) {
            // TODO
            return
        }

        var highestProgress = 0
        for (notification in activeNotifications) {
            if (notification.packageName == packageName) {
                val (notificationProgress, _) = getProgressBarValues(notification)
                val percentageProgress = getProgressFromPercentage(notification)
                if (notificationProgress > highestProgress) {
                    highestProgress = notificationProgress
                } else if (percentageProgress > highestProgress) {
                    highestProgress = percentageProgress
                }
            }
        }
        if (progress < highestProgress) {
            return
        }

        val progressBarMax = resources.getInteger(R.integer.progress_bar_max)
        val progressNormalized = if (progress in 1..progressMax) {
            (progress.toFloat() / progressMax.toFloat() * progressBarMax).roundToInt()
        } else {
            0
        }

        val intent = Intent(this, AccessibilityService::class.java)
        intent.putExtra("progress", progressNormalized)
        intent.putExtra("packageName", packageName)
        intent.putExtra("color", appInDatabase?.color ?: 1)
        intent.putExtra("priority", priorityLevel)
        startService(intent)
    }

    private fun sendRemovalRequestToAccessibilityService(packageName: String) {
        val intent = Intent(this, AccessibilityService::class.java)
        intent.putExtra("packageName", packageName)
        intent.putExtra("removal", true)
        startService(intent)
    }
}