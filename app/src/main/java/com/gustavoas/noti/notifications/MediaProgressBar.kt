package com.gustavoas.noti.notifications

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.gustavoas.noti.ProgressBarAppsRepository

class MediaProgressBar(
    ctx: Context,
    sbn: StatusBarNotification,
    appsRepository: ProgressBarAppsRepository
): TimedProgressBar(ctx, sbn, appsRepository) {
    override val priorityLevel: Int = 0

    private var mediaController: MediaController? = null
    private var mediaCallback: MediaController.Callback? = null

    init {
        updateNotification(sbn)
    }

    override fun updateNotification(sbn: StatusBarNotification) {
        super.updateNotification(sbn)

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val enabledForMedia = sharedPrefs.getBoolean("showForMedia", true)

        if (!enabledForMedia) {
            cancel()
            return
        }

        val mediaSession =
            sbn.notification.extras.get(NotificationCompat.EXTRA_MEDIA_SESSION) as? MediaSession.Token

        if (mediaSession == null) {
            cancel()
            return
        }

        val newMediaController = MediaController(ctx, mediaSession)
        if (mediaController == null) {
            createProgressBarFromMedia(newMediaController)
        }
    }

    private fun createProgressBarFromMedia(newMediaController: MediaController) {
        mediaController = newMediaController

        if (mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING) {
            startTrackingMediaPosition()
        }

        mediaCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                super.onPlaybackStateChanged(state)

                when (state?.state) {
                    PlaybackState.STATE_PLAYING -> {
                        startTrackingMediaPosition()
                    }
                    PlaybackState.STATE_NONE, PlaybackState.STATE_STOPPED,
                    PlaybackState.STATE_PAUSED, PlaybackState.STATE_ERROR -> {
                        cancel()
                    }
                    else -> {
                        stopUpdatingTimedPosition()
                    }
                }
            }
        }

        mediaController?.registerCallback(mediaCallback as MediaController.Callback)
    }

    private fun startTrackingMediaPosition() {
        val artwork = mediaController?.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        artwork?.let {
            getColorFromBitmap(it)?.let { color ->
                notificationColor = color
            }
        }

        startUpdatingTimedPosition(
            mediaController?.playbackState?.position ?: 0,
            mediaController?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
            mediaController?.playbackState?.playbackSpeed ?: 1f
        )
    }

    override fun cancel() {
        super.cancel()

        if (mediaCallback != null) {
            mediaController?.unregisterCallback(mediaCallback as MediaController.Callback)
            mediaCallback = null
        }
        mediaController = null
    }
}