package com.gustavoas.noti.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import com.gustavoas.noti.R

class BarStylesListPreference(context: Context, attrs: AttributeSet?) : ListPreference(context, attrs) {

    override fun onClick() {
        val sharedPreferences = preferenceManager.sharedPreferences ?: return

        if (!sharedPreferences.getBoolean("advancedProgressBarStyle", false)) {
            AlertDialog.Builder(context)
                .setSingleChoiceItems(entries, getValueIndex()) { dialog, index ->
                    if (callChangeListener(entryValues[index].toString())) {
                        setValueIndex(index)
                    }
                    dialog.dismiss()
                }.setNeutralButton(context.resources.getString(R.string.advancedOptions)) { _, _ ->
                    showAdvancedDialog()
                }.setTitle(title).show()

        } else {
            showAdvancedDialog()
        }
    }

    private fun showAdvancedDialog() {
        val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val dialogView = layoutInflater.inflate(R.layout.advanced_style_dialog, null)
        val portraitStyleListview = dialogView.findViewById<ListView>(R.id.portraitStyleOptions)
        val landscapeStyleListview = dialogView.findViewById<ListView>(R.id.landscapeStyleOptions)

        val options = context.resources.getStringArray(R.array.progressBarStyle)
        val adapter = ArrayAdapter(context, androidx.appcompat.R.layout.select_dialog_singlechoice_material, options)
        portraitStyleListview.adapter = adapter
        landscapeStyleListview.adapter = adapter

        val sharedPreferences = preferenceManager.sharedPreferences ?: return

        if (!sharedPreferences.contains("progressBarStylePortrait") && !sharedPreferences.contains("progressBarStyleLandscape")) {
            sharedPreferences.edit()
                .putString(
                    "progressBarStylePortrait",
                    sharedPreferences.getString("progressBarStyle", "linear")
                )
                .putString(
                    "progressBarStyleLandscape",
                    sharedPreferences.getString("progressBarStyle", "linear")
                )
                .apply()
        }

        portraitStyleListview.setItemChecked(
            context.resources.getStringArray(R.array.progressBarStyleValues).indexOf(
                sharedPreferences.getString("progressBarStylePortrait", "linear")
            ), true
        )

        landscapeStyleListview.setItemChecked(
            context.resources.getStringArray(R.array.progressBarStyleValues).indexOf(
                sharedPreferences.getString("progressBarStyleLandscape", "linear")
            ), true
        )

        AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                sharedPreferences.edit()?.putBoolean("advancedProgressBarStyle", true)?.apply()
                sharedPreferences.edit()
                    .putString(
                        "progressBarStylePortrait",
                        context.resources.getStringArray(R.array.progressBarStyleValues)[portraitStyleListview.checkedItemPosition]
                    )
                    .putString(
                        "progressBarStyleLandscape",
                        context.resources.getStringArray(R.array.progressBarStyleValues)[landscapeStyleListview.checkedItemPosition]
                    )
                    .apply()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(context.resources.getString(R.string.reset)) { _, _ ->
                sharedPreferences.edit()?.putBoolean("advancedProgressBarStyle", false)?.apply()
            }
            .show()
    }

    private fun getValueIndex() = entryValues.indexOf(value)
}