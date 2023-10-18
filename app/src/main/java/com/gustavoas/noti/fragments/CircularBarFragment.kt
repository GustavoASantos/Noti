package com.gustavoas.noti.fragments

import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.gustavoas.noti.R
import com.gustavoas.noti.Utils

class CircularBarFragment : BasePreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "progressBarLocation") {
            updateMarginPreferencesVisibility()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.circular_bar_preferences, rootKey)

        updateMarginPreferencesVisibility()

        findPreference<Preference>("shareConfig")?.setOnPreferenceClickListener {
            startActivity(Utils.composeEmail(requireContext()))
            true
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        findPreference<Preference>("onlyInPortrait")?.isVisible = !sharedPreferences
            .getBoolean("disableInLandscape", false)

        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun updateMarginPreferencesVisibility() {
        val progressBarLocation = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString("progressBarLocation", "center")

        when (progressBarLocation) {
            "right" -> findPreference<Preference>("progressBarLocation")?.icon =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_alignment_right)

            "left" -> findPreference<Preference>("progressBarLocation")?.icon =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_alignment_left)

            else -> findPreference<Preference>("progressBarLocation")?.icon =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_alignment_center)
        }

        findPreference<Preference>("circularProgressBarMarginLeft")?.isVisible =
            (progressBarLocation == "center" || progressBarLocation == "left")
        findPreference<Preference>("circularProgressBarMarginRight")?.isVisible =
            (progressBarLocation == "center" || progressBarLocation == "right")
    }
}