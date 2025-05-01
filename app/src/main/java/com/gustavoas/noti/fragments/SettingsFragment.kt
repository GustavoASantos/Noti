package com.gustavoas.noti.fragments

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.children
import com.gustavoas.noti.R
import com.gustavoas.noti.Utils.hasAccessibilityPermission
import com.gustavoas.noti.Utils.hasNotificationListenerPermission
import com.gustavoas.noti.Utils.hasSystemAlertWindowPermission
import com.gustavoas.noti.Utils.showColorDialog
import com.gustavoas.noti.preferences.BannerPreference
import com.gustavoas.noti.services.NotificationListenerService
import com.kizitonwose.colorpreferencecompat.ColorPreferenceCompat
import eltos.simpledialogfragment.SimpleDialog
import eltos.simpledialogfragment.SimpleDialog.OnDialogResultListener.BUTTON_POSITIVE
import eltos.simpledialogfragment.color.SimpleColorDialog

class SettingsFragment : BasePreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener, SimpleDialog.OnDialogResultListener {
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "progressBarStyle" || key == "progressBarStylePortrait" || key == "progressBarStyleLandscape" || key == "advancedProgressBarStyle") {
            if (key != "advancedProgressBarStyle" && sharedPreferences?.getString(
                    key, "linear"
                ) == "circular" && sharedPreferences.getBoolean("showHolePunchInstruction", true)
            ) {
                Toast.makeText(
                    requireContext(), getString(R.string.holePunchInstruction), Toast.LENGTH_LONG
                ).show()
                sharedPreferences.edit { putBoolean("showHolePunchInstruction", false) }
            }
            updateProgressBarStyle()
        } else if (key == "progressBarColor") {
            updateColorPreferenceSummary()
        } else if (key == "useNotificationColor") {
            updateColorPreferenceState()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        updateSetupVisibility()

        updateProgressBarStyle()

        updatePermissionDependentPreferences()

        updateColorPreferenceState()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        if (!sharedPreferences.contains("batteryOptimizations")) {
            sharedPreferences.edit {
                putBoolean(
                    "batteryOptimizations",
                    Build.BRAND.lowercase() != "google"
                )
            }
        }

        val batterOptimizationsBanner = findPreference<BannerPreference>("batteryOptimizations")
        if (!sharedPreferences.getBoolean("batteryOptimizations", true)) {
            batterOptimizationsBanner?.isVisible = false
        }

        findPreference<Preference>("accessibilityPermission")?.setOnPreferenceClickListener {
            showAccessibilityDialog()
            true
        }

        findPreference<Preference>("progressBarColor")?.setOnPreferenceClickListener {
            val color = PreferenceManager.getDefaultSharedPreferences(requireContext()).getInt(
                    "progressBarColor", ContextCompat.getColor(requireContext(), R.color.purple_500)
                )
            showColorDialog(this, color, "colorPicker")
            true
        }

        findPreference<Preference>("notificationPermission")?.setOnPreferenceClickListener {
            requestNotificationAccess()
            true
        }
    }

    override fun onStart() {
        super.onStart()

        updateSetupVisibility()

        updateColorPreferenceSummary()

        updatePermissionDependentPreferences()
    }

    override fun onDestroy() {
        super.onDestroy()

        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onResult(dialogTag: String, which: Int, extras: Bundle): Boolean {
        if (which == BUTTON_POSITIVE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                    putBoolean(
                        "usingMaterialYouColor",
                        extras.getInt(SimpleColorDialog.COLOR) == ContextCompat.getColor(
                            requireContext(), R.color.system_accent_color
                        ) && extras.getInt(SimpleColorDialog.SELECTED_SINGLE_POSITION) != 19
                    )
                }
            }

            findPreference<ColorPreferenceCompat>("progressBarColor")?.value = extras.getInt(
                SimpleColorDialog.COLOR,
                ContextCompat.getColor(requireContext(), R.color.purple_500)
            )
        }
        return true
    }

    private fun updateColorPreferenceState() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val colorPreference = findPreference<ColorPreferenceCompat>("progressBarColor")
        val useNotificationColor = sharedPreferences.getBoolean("useNotificationColor", true)

        colorPreference?.isEnabled = !useNotificationColor
        colorPreference?.icon?.alpha = if (useNotificationColor) 80 else 255
    }

    private fun updateColorPreferenceSummary() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val color = sharedPreferences.getInt(
            "progressBarColor", ContextCompat.getColor(requireContext(), R.color.purple_500)
        )
        val colorPosition = resources.getIntArray(R.array.colorsArrayValues).indexOf(color)
        var colorName = resources.getStringArray(R.array.colorsArray).getOrNull(colorPosition)
        val useMaterialYou = sharedPreferences.getBoolean("usingMaterialYouColor", false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && colorName == null && useMaterialYou) {
            sharedPreferences.edit {
                putInt(
                    "progressBarColor", ContextCompat.getColor(
                        requireContext(), R.color.system_accent_color
                    )
                )
            }

            findPreference<ColorPreferenceCompat>("progressBarColor")?.value =
                ContextCompat.getColor(requireContext(), R.color.system_accent_color)
            colorName = resources.getString(R.string.colorMaterialYou)
        }

        findPreference<Preference>("progressBarColor")?.summary =
            colorName ?: "#${Integer.toHexString(color).drop(2).uppercase()}"
    }

    private fun updateProgressBarStyle() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val advancedStyleOptions = sharedPreferences.getBoolean("advancedProgressBarStyle", false)
        val progressBarStyle = sharedPreferences.getString("progressBarStyle", "linear")
        val progressBarStylePortrait =
            sharedPreferences.getString("progressBarStylePortrait", "linear")
        val progressBarStyleLandscape =
            sharedPreferences.getString("progressBarStyleLandscape", "linear")

        val anyLinear =
            (!advancedStyleOptions && progressBarStyle == "linear") || (advancedStyleOptions && (progressBarStylePortrait == "linear" || progressBarStyleLandscape == "linear"))
        val anyCircular =
            (!advancedStyleOptions && progressBarStyle == "circular") || (advancedStyleOptions && (progressBarStylePortrait == "circular" || progressBarStyleLandscape == "circular"))

        findPreference<Preference>("CircularBarFragment")?.isVisible = anyCircular
        findPreference<Preference>("LinearBarFragment")?.isVisible = anyLinear

        findPreference<Preference>("progressBarStyle")?.summary = if (advancedStyleOptions) {
            if (resources.configuration.orientation == ORIENTATION_LANDSCAPE) {
                resources.getStringArray(R.array.progressBarStyle).getOrNull(
                    resources.getStringArray(R.array.progressBarStyleValues)
                        .indexOf(progressBarStyleLandscape)
                )
            } else {
                resources.getStringArray(R.array.progressBarStyle).getOrNull(
                    resources.getStringArray(R.array.progressBarStyleValues)
                        .indexOf(progressBarStylePortrait)
                )
            }
        } else {
            resources.getStringArray(R.array.progressBarStyle).getOrNull(
                resources.getStringArray(R.array.progressBarStyleValues).indexOf(progressBarStyle)
            )
        }
    }

    private fun updatePermissionDependentPreferences() {
        findPreference<Preference>("showInLockScreen")?.isVisible =
            (hasAccessibilityPermission(requireContext()) || Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
        findPreference<Preference>("disableInFullScreen")?.isVisible =
            hasSystemAlertWindowPermission(requireContext())
    }

    private fun updateSetupVisibility() {
        val hasAccessibilityPermission = hasAccessibilityPermission(requireContext())
        val hasNotificationListenerPermission = hasNotificationListenerPermission(requireContext())
        val hasSystemAlertWindowPermission = hasSystemAlertWindowPermission(requireContext())

        findPreference<Preference>("accessibilityPermission")?.isVisible =
            !hasAccessibilityPermission
        findPreference<Preference>("notificationPermission")?.isVisible =
            !hasNotificationListenerPermission
        findPreference<Preference>("systemAlertWindowPermission")?.isVisible =
            !hasSystemAlertWindowPermission
        findPreference<PreferenceCategory>("setup")?.isVisible = findPreference<PreferenceCategory>("setup")?.children?.any { it.isVisible } == true
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.prefsAccessibilityPermissionTitle)
            .setMessage(R.string.prefsAccessibilityPermissionSummary)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun requestNotificationAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            intent.putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(
                requireContext(), NotificationListenerService::class.java
            ).flattenToString()
            )
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
        } else {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
    }
}