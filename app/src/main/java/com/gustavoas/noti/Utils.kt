package com.gustavoas.noti

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import com.gustavoas.noti.model.ProgressBarApp
import com.gustavoas.noti.services.AccessibilityService
import com.gustavoas.noti.services.NotificationListenerService
import eltos.simpledialogfragment.color.SimpleColorDialog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

object Utils {
    fun hasAccessibilityPermission(context: Context): Boolean {
        val accessibilityServiceComponentName = ComponentName(context, AccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

        return enabledServices.orEmpty().let {
            it.contains(accessibilityServiceComponentName.flattenToString()) ||
                    it.contains(accessibilityServiceComponentName.flattenToShortString())
        }
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

    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            return activeNetworkInfo != null && activeNetworkInfo.isConnected
        }
    }

    fun getFirebaseConfigStorageReference(): StorageReference {
        val storage = Firebase.storage
        val storageRef = storage.reference

        val brand = Build.BRAND.lowercase()
        var model = Build.DEVICE.lowercase().replace("\\W".toRegex(), "")

        if (brand == "xiaomi" || brand == "redmi" || brand == "poco") {
            while (model.endsWith("in")) {
                model = model.dropLast(2)
            }
        }

        return storageRef.child("configs/$brand/$model.xml")
    }

    fun shareConfigToFirebase(context: Context) {
        if (!isInternetAvailable(context)) {
            Toast.makeText(context, context.getString(R.string.shareConfigNoInternetMessage), Toast.LENGTH_SHORT).show()
            return
        }

        val configRef = getFirebaseConfigStorageReference()

        configRef.stream.addOnSuccessListener { taskSnapshot ->
            val inputStream: InputStream = taskSnapshot.stream

            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var length: Int
            while (inputStream.read(buffer).also { length = it } != -1) {
                outputStream.write(buffer, 0, length)
            }
            val existingFileContent = outputStream.toString(StandardCharsets.UTF_8.name())

            if (existingFileContent != buildConfig(context)) {
                uploadConfigToStorageRef(context, configRef)
            }

        }.addOnFailureListener {
            uploadConfigToStorageRef(context, configRef)
        }

        Toast.makeText(context, context.getString(R.string.shareConfigPositiveMessage), Toast.LENGTH_SHORT).show()
    }

    private fun uploadConfigToStorageRef(context: Context, storageRef: StorageReference) {
        val config = buildConfig(context)
        val xmlInputStream = ByteArrayInputStream(config.toByteArray(StandardCharsets.UTF_8))

        storageRef.putStream(xmlInputStream)
    }

    private fun buildConfig(context: Context): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        val stylePrefs = sharedPreferences.all.filterKeys { it.startsWith("progressBarStyle") && it.last().isDigit() }

        if (stylePrefs.isEmpty() || stylePrefs.none { it.value == "circular" }) {
            return ""
        }

        val config = StringBuilder()
        config.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        config.append("<device>\n")

        val sizes = stylePrefs.keys.map { str -> str.dropWhile { !it.isDigit() } }.distinct()

        sizes.forEach {
            config.append(buildConfigForDisplay(context, it)).append("\n")
        }

        config.append("</device>")

        return config.toString()
    }

    private fun buildConfigForDisplay(context: Context, display: String): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val height = display.split("x")[0].toInt()
        val width = display.split("x")[1].toInt()
        val configuration = sharedPreferences.getString("progressBarStyle$display", "linear")

        val displayConfig = StringBuilder()
        displayConfig.append("\t<display width=\"$width\" height=\"$height\" configuration=\"$configuration\">\n")

        if (configuration == "circular") {
            val size = sharedPreferences.getInt("circularProgressBarSize$display", 65)
            val marginTop = sharedPreferences.getInt("circularProgressBarTopOffset$display", 60)
            val offset = sharedPreferences.getInt("circularProgressBarHorizontalOffset$display", 0)

            displayConfig.append("\t\t<size>$size</size>\n")
            displayConfig.append("\t\t<topOffset>$marginTop</topOffset>\n")
            displayConfig.append("\t\t<offset>$offset</offset>\n")
        }

        displayConfig.append("\t</display>")

        return displayConfig.toString()
    }

    fun showColorDialog(fragment: Fragment, color: Int, tag: String, reset: Boolean = false) {
        if (reset) {
            SimpleColorDialog.build()
                .colorPreset(color)
                .colors(fragment.context, R.array.colorsArrayValues)
                .allowCustom(true)
                .showOutline(0x46000000)
                .gridNumColumn(5)
                .choiceMode(SimpleColorDialog.SINGLE_CHOICE)
                .neg()
                .neut(R.string.reset)
                .show(fragment, tag)
        } else {
            SimpleColorDialog.build()
                .colorPreset(color)
                .colors(fragment.context, R.array.colorsArrayValues)
                .allowCustom(true)
                .showOutline(0x46000000)
                .gridNumColumn(5)
                .choiceMode(SimpleColorDialog.SINGLE_CHOICE)
                .neg()
                .show(fragment, tag)
        }
    }

    fun getRealDisplayHeight(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)!!
            val metrics = DisplayMetrics()
            display.getRealMetrics(metrics)
            metrics.heightPixels
        }
    }

    private fun getRealDisplayWidth(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)!!
            val metrics = DisplayMetrics()
            display.getRealMetrics(metrics)
            metrics.widthPixels
        }
    }

    fun getScreenSmallSide(context: Context): Int {
        return minOf(getRealDisplayHeight(context), getRealDisplayWidth(context))
    }

    fun getScreenLargeSide(context: Context): Int {
        return maxOf(getRealDisplayHeight(context), getRealDisplayWidth(context))
    }

    fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                ContextCompat.getSystemService(context, VibratorManager::class.java)
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator == null) {
            return
        }

        vibrator.cancel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(1, 200))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(1)
        }
    }

    fun getStatusBarHeight(context: Context): Int {
        return context.resources.getDimensionPixelSize(
            (context.resources.getIdentifier(
                "status_bar_height",
                "dimen",
                "android"
            )))
    }

    fun getApplicationInfo(context: Context, packageName: String): ApplicationInfo? {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
        } catch (e: NameNotFoundException) {
            null
        }
    }

    fun getApplicationName(context: Context, packageName: String): String? {
        return context.packageManager.getApplicationLabel(
            getApplicationInfo(context, packageName) ?: return null
        ).toString()
    }

    fun getApplicationIcon(context: Context, packageName: String): Drawable? {
        return context.packageManager.getApplicationIcon(
            getApplicationInfo(context, packageName) ?: return null
        )
    }

    fun getColorForApp(context: Context, progressBarApp: ProgressBarApp): Int {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val useNotificationColor = sharedPrefs.getBoolean("useNotificationColor", false)
        val useMaterialYou = sharedPrefs.getBoolean("usingMaterialYouColor", false)

        if ((progressBarApp.useDefaultColor && useNotificationColor) ||
            (!progressBarApp.useDefaultColor && !progressBarApp.useMaterialYouColor)) {
            progressBarApp.color?.let { return it }
        }

        if ((progressBarApp.useMaterialYouColor) ||
            (progressBarApp.useDefaultColor && useMaterialYou)) {
            return ContextCompat.getColor(context, R.color.system_accent_color)
        }

        return sharedPrefs.getInt(
            "progressBarColor", ContextCompat.getColor(context, R.color.purple_500)
        )
    }
}