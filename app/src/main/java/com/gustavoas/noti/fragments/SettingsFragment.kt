package com.gustavoas.noti.fragments

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import com.gustavoas.noti.AccessibilityDialogPrefCompat
import com.gustavoas.noti.AccessibilityPermissionDialog
import com.gustavoas.noti.R
import com.gustavoas.noti.Utils.hasAccessibilityPermission
import com.gustavoas.noti.Utils.hasNotificationListenerPermission
import com.gustavoas.noti.Utils.hasSystemAlertWindowPermission

class SettingsFragment : BasePreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
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

        findPreference<Preference>("showForMedia")?.isVisible =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(this)
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
            .getInt(
                "progressBarColor",
                ContextCompat.getColor(requireContext(), R.color.purple_500)
            )
        val colorPosition = resources.getIntArray(R.array.colorsArrayValues).indexOf(color)
        val colorName = resources.getStringArray(R.array.colorsArray).getOrNull(colorPosition)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && colorName == null) {
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putInt(
                    "progressBarColor", ContextCompat.getColor(
                        requireContext(),
                        R.color.system_accent_color
                    )
                )
                .apply()
        }

        findPreference<Preference>("progressBarColor")?.summary =
            colorName ?: getString(R.string.colorMaterialYou)
    }

    private fun updateProgressBarStyleVisibility() {
        val progressBarStyle = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString("progressBarStyle", "linear")
        val useOnlyInPortrait = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getBoolean("onlyInPortrait", false)

        findPreference<Preference>("CircularBarFragment")?.isVisible =
            progressBarStyle == "circular"
        findPreference<Preference>("LinearBarFragment")?.isVisible =
            (progressBarStyle == "linear" || useOnlyInPortrait)
    }

    private fun updateShowInLockscreenVisibility() {
        findPreference<Preference>("showInLockScreen")?.isVisible =
            (hasAccessibilityPermission(requireContext()) || Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
    }

    private fun updateSetupVisibility() {
        val hasNotificationListenerPermission = hasNotificationListenerPermission(requireContext())
        val hasSystemAlertWindowPermission = hasSystemAlertWindowPermission(requireContext())

        // xiaomi, samsung, vivo, etc are killing the accessibility service in the background
        findPreference<Preference>("accessibilityPermission")?.isVisible = false
        findPreference<Preference>("notificationPermission")?.isVisible =
            !hasNotificationListenerPermission
        findPreference<Preference>("systemAlertWindowPermission")?.isVisible =
            !hasSystemAlertWindowPermission
        findPreference<PreferenceCategory>("setup")?.isVisible =
            !(hasNotificationListenerPermission && hasSystemAlertWindowPermission)
    }
}