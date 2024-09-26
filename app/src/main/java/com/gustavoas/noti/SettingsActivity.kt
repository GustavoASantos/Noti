package com.gustavoas.noti

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Xml
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
import com.google.firebase.storage.StorageException
import com.gustavoas.noti.Utils.dpToPx
import com.gustavoas.noti.Utils.getFirebaseConfigStorageReference
import com.gustavoas.noti.Utils.getScreenLargeSide
import com.gustavoas.noti.Utils.getScreenSmallSide
import com.gustavoas.noti.Utils.hasAccessibilityPermission
import com.gustavoas.noti.Utils.hasNotificationListenerPermission
import com.gustavoas.noti.Utils.hasSystemAlertWindowPermission
import com.gustavoas.noti.fragments.CircularBarFragment
import com.gustavoas.noti.fragments.LinearBarFragment
import com.gustavoas.noti.fragments.PerAppSettingsFragment
import com.gustavoas.noti.fragments.SettingsFragment
import com.gustavoas.noti.model.DeviceConfiguration
import com.gustavoas.noti.services.AccessibilityService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class SettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val previewFab by lazy { findViewById<ExtendedFloatingActionButton>(R.id.previewFab) }
    private val topAppBar by lazy { findViewById<MaterialToolbar>(R.id.topAppBar) }
    private val appBarLayout by lazy { findViewById<AppBarLayout>(R.id.appBarLayout) }
    private var offsetChangeListener: OnOffsetChangedListener? = null
    private val handler = Handler(Looper.getMainLooper())

    private val sizeDependentPrefs = arrayOf(
        Pair("advancedProgressBarStyle", false),
        Pair("progressBarStyle", "linear"),
        Pair("progressBarStylePortrait", "linear"),
        Pair("progressBarStyleLandscape", "linear"),
        Pair("circularProgressBarThickness", 15),
        Pair("circularProgressBarSize", 65),
        Pair("circularProgressBarMarginTop", 30),
        Pair("circularProgressBarHorizontalOffset", 0),
        Pair("linearProgressBarSize", 15),
        Pair("matchStatusBarHeight", false),
        Pair("linearProgressBarMarginTop", 0),
        Pair("showBelowNotch", false),
    )

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sizeDependentPrefs.any { it.first == key }) {
            val defaultValue = sizeDependentPrefs.first { it.first == key }.second
            val width = getScreenSmallSide(this)
            val height = getScreenLargeSide(this)

            moveSharedPreferenceValue(key + height + "x" + width, key!!, defaultValue)
        }
    }

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
            runBlocking {
                setupDeviceConfiguration()
            }
        }

        if (!sharedPreferences.contains("progressBarColor")) {
            setMaterialYouAsDefault()
        }

        setupSizeDependentPrefs()

        sharedPreferences
            .registerOnSharedPreferenceChangeListener(this)

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

    private fun setupSizeDependentPrefs() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val width = getScreenSmallSide(this)
        val height = getScreenLargeSide(this)

        for (pref in sizeDependentPrefs) {
            val relatedPrefs = sharedPreferences.all.filterKeys { it.startsWith(pref.first) }

            val sameRatio = relatedPrefs.filterKeys { it.length > pref.first.length && it[pref.first.length].isDigit() && it.drop(pref.first.length).split("x")[0].toInt() / it.drop(pref.first.length).split("x")[1].toInt() == height / width }

            if (sameRatio.isEmpty()) {
                continue
            }

            val closest = sameRatio.keys.minByOrNull { abs(it.drop(pref.first.length).split("x")[0].toInt() * it.drop(pref.first.length).split("x")[1].toInt() - height * width) }

            var reductionRatio = 1f

            if (pref.second is Int) {
                reductionRatio = sqrt((height * width).div(closest!!.drop(pref.first.length).split("x")[0].toFloat() * closest.drop(pref.first.length).split("x")[1].toFloat()))
            }

            moveSharedPreferenceValue(pref.first, closest!!, pref.second, reductionRatio)
        }
    }

    private fun moveSharedPreferenceValue(key: String, oldKey: String, defaultValue: Any, reductionRatio: Float = 1f) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = sharedPreferences.edit()

        when (defaultValue) {
            is Boolean -> editor.putBoolean(key, sharedPreferences.getBoolean(oldKey, defaultValue))
            is Int -> editor.putInt(key, sharedPreferences.getInt(oldKey, defaultValue).times(reductionRatio).roundToInt())
            is String -> editor.putString(key, sharedPreferences.getString(oldKey, defaultValue))
        }

        editor.apply()
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
                    val sendEmail = Intent(Intent.ACTION_SENDTO).apply {
                        data =
                            Uri.parse("mailto:gustavoasgas1@gmail.com" + "?subject=" + Uri.encode("Noti"))
                    }
                    sendEmail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(sendEmail)
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

    override fun onDestroy() {
        super.onDestroy()

        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
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

    private suspend fun setupDeviceConfiguration() {
        if (!Utils.isInternetAvailable(this)) {
            return
        }

        val configRef = getFirebaseConfigStorageReference()

        try {
            val taskSnapshot = configRef.stream.await()

            val inputStream: InputStream = taskSnapshot.stream

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            sharedPreferences.edit()
                .putString("progressBarStyle", "circular")
                .apply()

            parseDeviceConfiguration(inputStream)

        } catch (e: StorageException) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            sharedPreferences.edit()
                .putString("progressBarStyle", "linear")
                .apply()
        }
    }

    private fun parseDeviceConfiguration(input: InputStream) {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setInput(input, null)

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "display") {
                        val displayConfig = DeviceConfiguration()
                        displayConfig.deviceWidth = parser.getAttributeValue(null, "width")
                        displayConfig.deviceHeight = parser.getAttributeValue(null, "height")
                        displayConfig.configuration = parser.getAttributeValue(null, "configuration")
                        parseDisplay(displayConfig, parser)
                    }
                }
            }
            parser.next()
        }
    }

    private fun parseDisplay(config: DeviceConfiguration, parser: XmlPullParser) {
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "size" -> config.size = parser.nextText()
                        "marginTop" -> config.marginTop = parser.nextText()
                        "offset" -> config.horizontalOffset = parser.nextText()
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "display") {
                        break
                    }
                }
            }
            parser.next()
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        val currentWidth = getScreenSmallSide(this)
        val currentHeight = getScreenLargeSide(this)

        if (config.deviceWidth == "" || config.deviceHeight == "") {
            if ((config.deviceWidth != "" && currentWidth == config.deviceWidth?.toInt()) ||
                (config.deviceHeight != "" && currentHeight == config.deviceHeight?.toInt())) {
                config.deviceWidth = currentWidth.toString()
                config.deviceHeight = currentHeight.toString()
            }

            if (config.deviceWidth == "" && config.deviceHeight != "") {
                config.deviceWidth = (currentWidth * config.deviceHeight!!.toInt() / currentHeight).toString()
            } else if (config.deviceHeight == "" && config.deviceWidth != "") {
                config.deviceHeight = (currentHeight * config.deviceWidth!!.toInt() / currentWidth).toString()
            }

            if (config.deviceWidth == "" || config.deviceHeight == "") {
                config.deviceWidth = currentWidth.toString()
                config.deviceHeight = currentHeight.toString()
            }
        }

        val appendix = config.deviceHeight + "x" + config.deviceWidth

        if (config.configuration == "circular") {
            sharedPreferences.edit()
                .putString("progressBarStyle$appendix", "circular")
                .putBoolean("blackBackground", true)
                .putInt("circularProgressBarSize$appendix", config.size?.toIntOrNull() ?: 65)
                .putInt("circularProgressBarMarginTop$appendix", config.marginTop?.toIntOrNull() ?: 30)
                .putInt("circularProgressBarHorizontalOffset$appendix", config.horizontalOffset?.toIntOrNull() ?: 0)
                .apply()
        } else {
            sharedPreferences.edit()
                .putString("progressBarStyle$appendix", "linear")
                .apply()
        }
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