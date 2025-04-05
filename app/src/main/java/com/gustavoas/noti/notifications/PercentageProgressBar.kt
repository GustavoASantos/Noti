package com.gustavoas.noti.notifications

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.gustavoas.noti.ProgressBarAppsRepository
import kotlin.math.roundToInt

class PercentageProgressBar(
    ctx: Context,
    sbn: StatusBarNotification,
    appsRepository: ProgressBarAppsRepository
): ProgressBarNotification(ctx, sbn, appsRepository) {
    override val priorityLevel: Int = 2

    private var initialValue: Int? = null

    init {
        updateNotification(sbn)
    }

    override fun updateNotification(sbn: StatusBarNotification) {
        super.updateNotification(sbn)

        val percentageProgress = getProgressFromPercentage(sbn)

        if (percentageProgress == 0) {
            cancel()
            return
        }

        if (initialValue != null && initialValue != percentageProgress) {
            sendProgressToAccessibilityService(percentageProgress, 100)
        }

        if (initialValue == null) {
            initialValue = percentageProgress
        }
    }

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
}