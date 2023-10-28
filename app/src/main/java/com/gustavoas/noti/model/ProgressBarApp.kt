package com.gustavoas.noti.model

data class ProgressBarApp(
    val packageName: String,
    var showProgressBar: Boolean,
    var color: Int = 1
)