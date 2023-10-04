package com.gustavoas.noti

import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.gustavoas.noti.Utils.hasAccessibilityPermission
import com.gustavoas.noti.Utils.hasNotificationListenerPermission
import com.gustavoas.noti.Utils.hasSystemAlertWindowPermission
import com.gustavoas.noti.fragments.CircularBarFragment
import com.gustavoas.noti.fragments.LinearBarFragment
import com.gustavoas.noti.fragments.PerAppSettingsFragment
import com.gustavoas.noti.fragments.SettingsFragment
import com.gustavoas.noti.model.DeviceConfiguration
import com.gustavoas.noti.services.AccessibilityService
import org.xmlpull.v1.XmlPullParser

class SettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    private val previewFab by lazy { findViewById<ExtendedFloatingActionButton>(R.id.previewFab) }
    private val topAppBar by lazy { findViewById<MaterialToolbar>(R.id.topAppBar) }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setTheme(R.style.SplashScreen)
            installSplashScreen()
        } else {
            setTheme(R.style.Theme_NotiProgressBar)
        }

        super.onCreate(savedInstanceState)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (!sharedPreferences.contains("progressBarStyle")) {
            setupDeviceConfiguration()
        }

        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commitNow()
        }

        updateUpNavigationVisibility()
    }

    override fun onStart() {
        super.onStart()

        if (hasNotificationListenerPermission(this) && (hasAccessibilityPermission(this) || hasSystemAlertWindowPermission(this))) {
            previewFab.visibility = View.VISIBLE
        } else {
            previewFab.visibility = View.GONE
        }

        previewFab.setOnClickListener {
            simulateDownload()
        }

        supportFragmentManager.addOnBackStackChangedListener {
            updateUpNavigationVisibility()
        }

        topAppBar.setNavigationOnClickListener {
            supportFragmentManager.popBackStack()
        }

        topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.bug_report -> {
                    startActivity(composeEmail())
                    true
                }

                else -> false
            }
        }
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val fragment = when (pref.key) {
            "CircularBarFragment" -> CircularBarFragment()
            "LinearBarFragment" -> LinearBarFragment()
            "PerAppSettingsFragment" -> PerAppSettingsFragment()
            else -> null
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, fragment ?: return false)
            .addToBackStack(null)
            .commit()

        return true
    }

    private fun simulateDownload () {
        val intent = Intent(this, AccessibilityService::class.java)
        for (i in 25..125 step 25) {
            Handler(Looper.getMainLooper()).postDelayed({
                intent.putExtra("progress", i)
                intent.putExtra("progressMax", 100)
                intent.putExtra("removal", i > 100)
                startService(intent)
            }, (i * 40 - 1000).toLong())
        }
    }

    private fun composeEmail(): Intent {
        val sendEmail = Intent(Intent.ACTION_SENDTO)
            .setData(Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf("gustavoasgas1@gmail.com"))
            .putExtra(Intent.EXTRA_SUBJECT, "Noti")

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (sharedPreferences.getString("progressBarStyle", "linear") == "circular") {
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

    private fun setupDeviceConfiguration() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        val userScreenSmallSide =
            resources.displayMetrics.widthPixels.coerceAtMost(resources.displayMetrics.heightPixels)
        val xmlResourceId = resources.getIdentifier(
            "device_" + Build.BRAND + "_" + Build.DEVICE + "_" + userScreenSmallSide,
            "xml", this.packageName
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

    private fun updateUpNavigationVisibility() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            topAppBar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            topAppBar.setNavigationIconTint(ContextCompat.getColor(this, R.color.text))
        } else {
            topAppBar.navigationIcon = null
        }
    }
}