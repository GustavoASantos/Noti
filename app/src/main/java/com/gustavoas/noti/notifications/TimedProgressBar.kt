package com.gustavoas.noti.notifications

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import com.gustavoas.noti.ProgressBarAppsRepository
import kotlin.math.abs

abstract class TimedProgressBar(
    ctx: Context,
    sbn: StatusBarNotification,
    appsRepository: ProgressBarAppsRepository
): ProgressBarNotification(ctx, sbn, appsRepository) {
    private val updateInterval = 1000

    private val handler = Handler(Looper.getMainLooper())

    private var updatesRunnable: Runnable? = null

    protected fun startUpdatingTimedPosition(
        initialPosition: Long,
        duration: Long,
        speed: Float
    ) {
        stopUpdatingTimedPosition()

        val initialTime = System.currentTimeMillis()
        updatesRunnable = object : Runnable {
            override fun run() {
                val currTime = System.currentTimeMillis()
                var currProgress = (initialPosition + (currTime - initialTime) * speed).toLong()

                if (currProgress !in 0..duration) {
                    return
                }

                val updateStep = abs(updateInterval * speed).toInt()
                if (duration - currProgress < updateStep) {
                    currProgress = duration
                } else if (currProgress < updateStep) {
                    currProgress = 0
                }

                sendProgressToAccessibilityService(
                    currProgress.toInt(),
                    duration.toInt(),
                )

                updatesRunnable?.let { handler.postDelayed(it, updateInterval.toLong()) }
            }
        }

        updatesRunnable?.let { handler.post(it) }
    }

    protected fun stopUpdatingTimedPosition() {
        handler.removeCallbacksAndMessages(null)
        updatesRunnable = null
    }

    override fun cancel() {
        super.cancel()

        stopUpdatingTimedPosition()
    }
}