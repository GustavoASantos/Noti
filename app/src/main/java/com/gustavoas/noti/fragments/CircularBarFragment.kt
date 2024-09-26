package com.gustavoas.noti.fragments

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.gustavoas.noti.R
import com.gustavoas.noti.Utils

class CircularBarFragment : BasePreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // TODO: If offset is not zero and hole punch size changes toast suggesting a 0.5x change
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.circular_bar_preferences, rootKey)

        findPreference<Preference>("shareConfig")?.setOnPreferenceClickListener {
            Utils.shareConfigToFirebase(requireContext())
            true
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
    }
}