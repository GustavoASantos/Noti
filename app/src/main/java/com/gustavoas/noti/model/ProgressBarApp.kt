package com.gustavoas.noti.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ProgressBarApp(
    val packageName: String,
    var showProgressBar: Boolean = true,
    var color: Int? = null,
    var useDefaultColor: Boolean = true,
    var useMaterialYouColor: Boolean = false
) : Parcelable