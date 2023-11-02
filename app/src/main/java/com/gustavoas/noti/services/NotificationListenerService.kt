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
import com.gustavoas.noti.model.ProgressBarApp

class NotificationListenerService : NotificationListenerService() {
    private val appsRepository by lazy { ProgressBarAppsRepository.getInstance(this) }
    private var mediaController: MediaController? = null
    private var callback: MediaController.Callback? = null
    private val handler = Handler(Looper.getMainLooper())

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

        if (hasProgressBarNotIndeterminate(sbn)) {
            val (progress, progressMax) = getProgressBarValues(sbn)
            val percentageProgress = getProgressFromPercentage(sbn).toInt()
            val showForDownloads = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("showForDownloads", true)

            if (progress > 0 && progressMax > 0 && showForDownloads) {
                sendProgressToAccessibilityService(
                    progress, progressMax, packageName = sbn.packageName
                )
            } else if (percentageProgress in 1..100 && showForDownloads) {
                sendProgressToAccessibilityService(
                    percentageProgress, 100, packageName = sbn.packageName
                )
            } else {
                sendProgressToAccessibilityService(removal = true)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        val showForMedia = sharedPreferences.getBoolean("showForMedia", true)
        val showForDownloads = sharedPreferences.getBoolean("showForDownloads", true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && showForMedia && mediaController?.packageName == sbn.packageName) {
            stopUpdatingMediaPosition()
        } else if ((hasProgressBarNotIndeterminate(sbn) && showForDownloads) || activeNotifications.isEmpty()) {
            sendProgressToAccessibilityService(removal = true)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun createProgressBarFromMedia(newMediaController: MediaController) {
        mediaController = newMediaController

        startUpdatingMediaPosition(
            mediaController?.playbackState?.position?.toInt() ?: 0,
            mediaController?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.toInt() ?: 0,
            mediaController?.playbackState?.playbackSpeed ?: 1f,
            mediaController?.packageName ?: ""
        )

        callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                super.onPlaybackStateChanged(state)
                if (state?.state == PlaybackState.STATE_PLAYING) {
                    handler.removeCallbacksAndMessages(null)
                    startUpdatingMediaPosition(
                        state.position.toInt(),
                        mediaController?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.toInt() ?: 0,
                        state.playbackSpeed,
                        mediaController?.packageName ?: ""
                    )
                } else {
                    stopUpdatingMediaPosition()
                }
            }
        }

        mediaController?.registerCallback(callback as MediaController.Callback)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startUpdatingMediaPosition(
        initialProgress: Int, duration: Int, speed: Float, packageName: String
    ) {
        var currProgress = initialProgress
        val runnable = object : Runnable {
            override fun run() {
                if (currProgress <= duration && PreferenceManager.getDefaultSharedPreferences(this@NotificationListenerService)
                        .getBoolean(
                            "showForMedia", true
                        ) && appsRepository.getApp(packageName)?.showProgressBar != false
                ) {
                    sendProgressToAccessibilityService(
                        currProgress, duration, packageName = packageName, lowPriority = true
                    )
                    currProgress += (1000 * speed).toInt()
                    if (duration - currProgress in 0 .. (1000 * speed).toInt()) {
                        currProgress = duration
                    }
                    handler.postDelayed(this, 1000)
                } else {
                    stopUpdatingMediaPosition()
                }
            }
        }
        handler.postDelayed(runnable, 0)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun stopUpdatingMediaPosition() {
        sendProgressToAccessibilityService(removal = true)
        handler.removeCallbacksAndMessages(null)
        if (callback != null) {
            mediaController?.unregisterCallback(callback as MediaController.Callback)
            callback = null
        }
        mediaController = null
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

    private fun showForApp(appInDatabase: ProgressBarApp?, packageName: String): Boolean {
        return if (appInDatabase == null) {
            if (packageName.isNotEmpty()) appsRepository.addApp(ProgressBarApp(packageName, true))
            true
        } else appInDatabase.showProgressBar
    }

    private fun sendProgressToAccessibilityService(
        progress: Int = 0,
        progressMax: Int = 0,
        removal: Boolean = false,
        packageName: String = "",
        lowPriority: Boolean = false
    ) {
        val appInDatabase = appsRepository.getApp(packageName)
        if (!showForApp(appInDatabase, packageName)) {
            return
        }

        val intent = Intent(this, AccessibilityService::class.java)
        intent.putExtra("progress", progress)
        intent.putExtra("progressMax", progressMax)
        intent.putExtra("color", appInDatabase?.color ?: 1)
        intent.putExtra("removal", removal)
        intent.putExtra("lowPriority", lowPriority)
        startService(intent)
    }
}