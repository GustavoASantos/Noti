package com.gustavoas.noti

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.preference.PreferenceManager
import com.gustavoas.noti.services.AccessibilityService
import com.gustavoas.noti.services.NotificationListenerService

object Utils {
    fun hasAccessibilityPermission(context: Context): Boolean {
        val accessibilityServiceComponentName = ComponentName(context, AccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services")
        return enabledServices?.contains(accessibilityServiceComponentName.flattenToString()) ?: false
    }

    fun hasNotificationListenerPermission(context: Context): Boolean {
        val notificationListenerComponentName = ComponentName(context, NotificationListenerService::class.java)
        val enabledServices = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return enabledServices?.contains(notificationListenerComponentName.flattenToString()) ?: false
    }

    fun hasSystemAlertWindowPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun composeEmail(context: Context): Intent {
        val sendEmail = Intent(Intent.ACTION_SENDTO)
            .setData(Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf("gustavoasgas1@gmail.com"))
            .putExtra(Intent.EXTRA_SUBJECT, "Noti")

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (sharedPreferences.getString("progressBarStyle", "linear") == "circular") {
            val resources = context.resources
            val deviceScreenSize =
                resources.displayMetrics.widthPixels.coerceAtMost(resources.displayMetrics.heightPixels)
            val location = sharedPreferences.getString("progressBarLocation", "center")
            val size = sharedPreferences.getInt("circularProgressBarSize", 70).plus(10)
            val marginTop = sharedPreferences.getInt("circularProgressBarMarginTop", 70).plus(10)
            val marginLeft = sharedPreferences.getInt("circularProgressBarMarginLeft", 70).plus(10)
            val marginRight = sharedPreferences.getInt("circularProgressBarMarginRight", 70).plus(10)
            sendEmail.putExtra(
                Intent.EXTRA_TEXT,
                "Circular progress bar configuration:\n" +
                        "<device brand=${Build.BRAND} device=${Build.DEVICE} resolution=$deviceScreenSize>\n" +
                        "   <location>$location</location>\n" +
                        "   <size>$size</size>\n" +
                        "   <marginTop>$marginTop</marginTop>\n" +
                        "   <marginLeft>$marginLeft</marginLeft>\n" +
                        "   <marginRight>$marginRight</marginRight>\n" +
                        "</device>"
            )
        }

        return sendEmail
    }
}