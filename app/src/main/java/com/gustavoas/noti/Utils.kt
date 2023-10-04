package com.gustavoas.noti

import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.provider.Settings
import androidx.preference.PreferenceManager
import com.gustavoas.noti.model.DeviceConfiguration
import com.gustavoas.noti.services.AccessibilityService
import com.gustavoas.noti.services.NotificationListenerService
import org.xmlpull.v1.XmlPullParser

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

    fun setupDeviceConfiguration(context: Context) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val resources = context.resources

        val userScreenSmallSide =
            resources.displayMetrics.widthPixels.coerceAtMost(resources.displayMetrics.heightPixels)
        val xmlResourceId = resources.getIdentifier(
            "device_" + Build.BRAND + "_" + Build.DEVICE + "_" + userScreenSmallSide,
            "xml", context.packageName
        )
        val parser: XmlPullParser =
            try {
                resources.getXml(xmlResourceId)
            } catch (e: Resources.NotFoundException) {
                sharedPreferences.edit()
                    .putString("progressBarStyle", "linear")
                    .apply()
                return
            }

        val deviceConfig = DeviceConfiguration()

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "location" -> deviceConfig.location = parser.nextText()
                        "size" -> deviceConfig.size = parser.nextText()
                        "marginTop" -> deviceConfig.marginTop = parser.nextText()
                        "marginLeft" -> deviceConfig.marginLeft = parser.nextText()
                        "marginRight" -> deviceConfig.marginRight = parser.nextText()
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "device") {
                        break
                    }
                }
            }
            parser.next()
        }

        sharedPreferences.edit()
            .putString("progressBarStyle", "circular")
            .putString("progressBarLocation", deviceConfig.location)
            .putInt("circularProgressBarSize", deviceConfig.size?.toIntOrNull()?.minus(10) ?: 70)
            .putInt("circularProgressBarMarginTop", deviceConfig.marginTop?.toIntOrNull()?.minus(10) ?: 70)
            .putInt("circularProgressBarMarginLeft", deviceConfig.marginLeft?.toIntOrNull()?.minus(10) ?: 70)
            .putInt("circularProgressBarMarginRight",deviceConfig.marginRight?.toIntOrNull()?.minus(10) ?: 70)
            .apply()
    }
}