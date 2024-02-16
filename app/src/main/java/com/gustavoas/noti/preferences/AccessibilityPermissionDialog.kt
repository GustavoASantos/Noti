package com.gustavoas.noti.preferences

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.AttributeSet
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat

class AccessibilityPermissionDialog(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs)

class AccessibilityDialogPrefCompat: PreferenceDialogFragmentCompat() {
    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    companion object {
        fun newInstance(key: String): AccessibilityDialogPrefCompat {
            val fragment = AccessibilityDialogPrefCompat()
            val bundle = Bundle(1)
            bundle.putString(ARG_KEY, key)
            fragment.arguments = bundle
            return fragment
        }
    }
}