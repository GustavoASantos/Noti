package com.gustavoas.noti

import android.content.Intent
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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.gustavoas.noti.Permissions.hasAccessibilityPermission
import com.gustavoas.noti.Permissions.hasNotificationListenerPermission
import com.gustavoas.noti.fragments.CircularBarFragment
import com.gustavoas.noti.fragments.LinearBarFragment
import com.gustavoas.noti.fragments.PerAppSettingsFragment
import com.gustavoas.noti.fragments.SettingsFragment
import com.gustavoas.noti.services.AccessibilityService

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

        if (hasNotificationListenerPermission(this) && (hasAccessibilityPermission(this) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1)) {
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

    private fun updateUpNavigationVisibility() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            topAppBar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            topAppBar.setNavigationIconTint(ContextCompat.getColor(this, R.color.text))
        } else {
            topAppBar.navigationIcon = null
        }
    }
}