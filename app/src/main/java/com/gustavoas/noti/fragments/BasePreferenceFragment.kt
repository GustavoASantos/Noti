package com.gustavoas.noti.fragments

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import com.gustavoas.noti.Utils

abstract class BasePreferenceFragment : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val preferencesView = listView
        preferencesView.setPadding(0, 0, 0, Utils.dpToPx(requireContext(), 100))
        preferencesView.isVerticalScrollBarEnabled = false
    }
}