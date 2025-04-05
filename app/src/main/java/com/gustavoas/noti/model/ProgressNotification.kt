package com.gustavoas.noti.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ProgressNotification (
    val progressBarApp: ProgressBarApp,
    val progress: Int,
    val priority: Int
) : Parcelable