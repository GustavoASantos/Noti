package com.gustavoas.noti.fragments

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.gustavoas.noti.AccessibilityDialogPrefCompat
import com.gustavoas.noti.AccessibilityPermissionDialog
import com.gustavoas.noti.R
import com.gustavoas.noti.Utils.dpToPx
import com.gustavoas.noti.Utils.hasAccessibilityPermission
import com.gustavoas.noti.Utils.hasNotificationListenerPermission
import com.gustavoas.noti.Utils.hasSystemAlertWindowPermission

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "progressBarStyle" || key == "onlyInPortrait") {
            updateProgressBarStyleVisibility()
        } else if (key == "progressBarColor") {
            updateColorPreferenceSummary()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        updateSetupVisibility()

        updateProgressBarStyleVisibility()

        updateShowInLockscreenVisibility()

        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val preferencesView = listView
        preferencesView.setPadding(0, 0, 0, dpToPx(requireContext(), 100))
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is AccessibilityPermissionDialog) {
            val dialogFragment = AccessibilityDialogPrefCompat.newInstance(preference.key)
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, null)
        } else
            super.onDisplayPreferenceDialog(preference)
    }

    override fun onStart() {
        super.onStart()

        updateSetupVisibility()

        updateColorPreferenceSummary()

        updateShowInLockscreenVisibility()
    }

    override fun onDestroy() {
        super.onDestroy()

        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun updateColorPreferenceSummary() {
        val color = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getInt("progressBarColor", ContextCompat.getColor(requireContext(), R.color.purple_500))
        val colorPosition = resources.getIntArray(R.array.colorsArrayValues).indexOf(color)
        val colorName = resources.getStringArray(R.array.colorsArray).getOrNull(colorPosition)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && colorName == null) {
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putInt("progressBarColor", ContextCompat.getColor(requireContext(),
                    R.color.system_accent_color
                ))
                .apply()
        }

        findPreference<Preference>("progressBarColor")?.summary = colorName ?: getString(R.string.colorMaterialYou)
    }

    private fun updateProgressBarStyleVisibility() {
        val progressBarStyle = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString("progressBarStyle", "linear")
        val useOnlyInPortrait = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getBoolean("onlyInPortrait", false)

        findPreference<Preference>("CircularBarFragment")?.isVisible = progressBarStyle == "circular"
        findPreference<Preference>("LinearBarFragment")?.isVisible = (progressBarStyle == "linear" || useOnlyInPortrait)
    }

    private fun updateShowInLockscreenVisibility() {
        findPreference<Preference>("showInLockScreen")?.isVisible = (hasAccessibilityPermission(requireContext()) || Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
    }

    private fun updateSetupVisibility() {
        val hasAccessibilityPermission = hasAccessibilityPermission(requireContext())
        val hasNotificationListenerPermission = hasNotificationListenerPermission(requireContext())
        val hasSystemAlertWindowPermission = hasSystemAlertWindowPermission(requireContext())

        findPreference<Preference>("accessibilityPermission")?.isVisible = (!hasAccessibilityPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        findPreference<Preference>("notificationPermission")?.isVisible = !hasNotificationListenerPermission
        findPreference<Preference>("systemAlertWindowPermission")?.isVisible = !hasSystemAlertWindowPermission
        findPreference<PreferenceCategory>("setup")?.isVisible = !((hasAccessibilityPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) && hasNotificationListenerPermission && hasSystemAlertWindowPermission)
    }
}

class CircularBarFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "progressBarLocation") {
            updateMarginPreferencesVisibility()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.circular_bar_preferences, rootKey)

        updateMarginPreferencesVisibility()

        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val preferencesView = listView
        preferencesView.setPadding(0, 0, 0, dpToPx(requireContext(), 100))
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
            "right" -> findPreference<Preference>("progressBarLocation")?.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_alignment_right)
            "left" -> findPreference<Preference>("progressBarLocation")?.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_alignment_left)
            else -> findPreference<Preference>("progressBarLocation")?.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_alignment_center)
        }

        findPreference<Preference>("circularProgressBarMarginLeft")?.isVisible = (progressBarLocation == "center" || progressBarLocation == "left")
        findPreference<Preference>("circularProgressBarMarginRight")?.isVisible = (progressBarLocation == "center" || progressBarLocation == "right")
    }
}

class LinearBarFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.linear_bar_preferences, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val preferencesView = listView
        preferencesView.setPadding(0, 0, 0, dpToPx(requireContext(), 100))
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