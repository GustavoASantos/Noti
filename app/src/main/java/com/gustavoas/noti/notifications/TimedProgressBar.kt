package com.gustavoas.noti.notifications

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import com.gustavoas.noti.ProgressBarAppsRepository

abstract class TimedProgressBar(
    ctx: Context,
    sbn: StatusBarNotification,
    appsRepository: ProgressBarAppsRepository
): ProgressBarNotification(ctx, sbn, appsRepository) {
    protected val handler = Handler(Looper.getMainLooper())

    protected fun startUpdatingTimedPosition(
        initialPosition: Long,
        duration: Long,
        speed: Float
    ) {
        // TODO: check if this is necessary
        handler.removeCallbacksAndMessages(null)
        val updateInterval = 1000
        var currProgress = initialPosition
        val initialTime = System.currentTimeMillis()
        val runnable = object : Runnable {
            override fun run() {
                if (currProgress !in 0..duration) {
                    return
                }

                val currTime = System.currentTimeMillis()

                currProgress = (initialPosition + (currTime - initialTime) * speed).toLong()
                if (speed > 0 && duration - currProgress in 1 until (updateInterval * speed).toInt()) {
                    currProgress = duration
                } else if (speed < 0 && currProgress in 2..updateInterval) {
                    currProgress = 1
                }

                sendProgressToAccessibilityService(
                    currProgress.toInt(),
                    duration.toInt(),
                )

                handler.postDelayed(this, updateInterval.toLong())
            }
        }
        runnable.run()
    }

    override fun cancel() {
        super.cancel()

        handler.removeCallbacksAndMessages(null)
    }
}