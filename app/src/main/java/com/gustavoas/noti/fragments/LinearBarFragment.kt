package com.gustavoas.noti.fragments

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.gustavoas.noti.R
import com.gustavoas.noti.Utils.getStatusBarHeight

class LinearBarFragment : BasePreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "linearProgressBarSize") {
            val size = sharedPreferences?.getInt(key, 15) ?: 15
            sharedPreferences?.edit()
                ?.putBoolean("matchStatusBarHeight", size == getStatusBarHeight(context ?: return))
                ?.apply()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.linear_bar_preferences, rootKey)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        findPreference<Preference>("statusBarHeightCard")?.summary =
            getString(R.string.prefsStatusBarHeightInfo, getStatusBarHeight(requireContext()))

        if (sharedPreferences.getBoolean("matchStatusBarHeight", false)) {
            sharedPreferences.edit()
                .putInt("linearProgressBarSize", getStatusBarHeight(requireContext())).apply()
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !hasDisplayCutout()) {
                findPreference<Preference>("showBelowNotch")?.isVisible = false
            }
        }
    }

    private fun hasDisplayCutout(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val windowInsets = requireActivity().window?.decorView?.rootWindowInsets
            val displayCutout = windowInsets?.displayCutout
            displayCutout != null
        } else {
            false
        }
    }
}