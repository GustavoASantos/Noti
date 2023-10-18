package com.gustavoas.noti.fragments

import android.os.Build
import android.os.Bundle
import androidx.preference.Preference
import com.gustavoas.noti.R

class LinearBarFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.linear_bar_preferences, rootKey)
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