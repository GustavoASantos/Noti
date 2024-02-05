package com.gustavoas.noti

import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.gustavoas.noti.Utils.composeEmail
import com.gustavoas.noti.Utils.dpToPx
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
    private val appBarLayout by lazy { findViewById<AppBarLayout>(R.id.appBarLayout) }
    private var offsetChangeListener: OnOffsetChangedListener? = null
    private val handler = Handler(Looper.getMainLooper())

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

        if (!sharedPreferences.contains("progressBarColor")) {
            setMaterialYouAsDefault()
        }

        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commitNow()
        }

        updateUpNavigationVisibility()

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView.rootView) { _, insets ->
            val keyboardInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (keyboardInset > 0) {
                appBarLayout.setExpanded(false, true)
            }
            insets
        }
    }

    override fun onStart() {
        super.onStart()

        val collapsingToolbarLayout = findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbar)
        var isVisible = true
        var scrollRange = -1

        offsetChangeListener =
            OnOffsetChangedListener { barLayout, verticalOffset ->
                if (scrollRange == -1) {
                    scrollRange = barLayout?.totalScrollRange!!
                }
                if (scrollRange + verticalOffset < dpToPx(this, 25)) {
                    collapsingToolbarLayout.title = resources.getString(R.string.app_name_short)
                    isVisible = true
                } else if (isVisible) {
                    collapsingToolbarLayout.title = resources.getString(R.string.app_name)
                    isVisible = false
                }
            }

        appBarLayout.addOnOffsetChangedListener(offsetChangeListener)

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
                    startActivity(composeEmail(this))
                    true
                }

                else -> false
            }
        }
    }

    override fun onStop() {
        super.onStop()

        if (offsetChangeListener != null) {
            appBarLayout.removeOnOffsetChangedListener(offsetChangeListener!!)
        }
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
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

    private fun simulateDownload() {
        val intent = Intent(this, AccessibilityService::class.java)
        handler.removeCallbacksAndMessages(null)
        for (i in 2500..12500 step 2500) {
            handler.postDelayed({
                intent.putExtra("progress", i)
                intent.putExtra("priority", 10)
                intent.putExtra("removal", i > 10000)
                intent.putExtra("packageName", packageName)
                startService(intent)
            }, (i * 0.4 - 1000).toLong())
        }
    }

    private fun setupDeviceConfiguration() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        val userScreenSmallSide =
            minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val xmlResourceId = resources.getIdentifier(
            "device_" + Build.BRAND.lowercase() + "_" + Build.DEVICE.lowercase().replace("\\W".toRegex(), "") + "_" + userScreenSmallSide,
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
            .putBoolean("blackBackground", true)
            .putString("progressBarLocation", deviceConfig.location ?: "center")
            .putInt("circularProgressBarSize", deviceConfig.size?.toIntOrNull()?.minus(10) ?: 70)
            .putInt("circularProgressBarMarginTop", deviceConfig.marginTop?.toIntOrNull()?.minus(10) ?: 70)
            .putInt("circularProgressBarMarginLeft", deviceConfig.marginLeft?.toIntOrNull()?.minus(10) ?: 70)
            .putInt("circularProgressBarMarginRight",deviceConfig.marginRight?.toIntOrNull()?.minus(10) ?: 70)
            .apply()
    }

    private fun setMaterialYouAsDefault() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sharedPreferences.edit()
                .putInt(
                    "progressBarColor", ContextCompat.getColor(
                        this,
                        R.color.system_accent_color
                    )
                )
                .putBoolean("usingMaterialYouColor", true)
                .apply()
        } else {
            sharedPreferences.edit()
                .putInt(
                    "progressBarColor", ContextCompat.getColor(
                        this,
                        R.color.purple_500
                    )
                ).apply()
        }
    }

    private fun updateUpNavigationVisibility() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            topAppBar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            topAppBar.setNavigationIconTint(ContextCompat.getColor(this, R.color.text))
            topAppBar.setTitleMargin(0, 0, dpToPx(this, 40), 0)
        } else {
            topAppBar.navigationIcon = null
            topAppBar.setTitleMargin(0, 0, 0, 0)
        }
    }
}