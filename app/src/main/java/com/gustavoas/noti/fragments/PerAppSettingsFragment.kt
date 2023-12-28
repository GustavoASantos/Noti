package com.gustavoas.noti.fragments

import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gustavoas.noti.ProgressBarAppsAdapter
import com.gustavoas.noti.ProgressBarAppsRepository
import com.gustavoas.noti.R
import com.gustavoas.noti.model.ProgressBarApp
import eltos.simpledialogfragment.SimpleDialog
import eltos.simpledialogfragment.SimpleDialog.OnDialogResultListener.BUTTON_NEUTRAL
import eltos.simpledialogfragment.SimpleDialog.OnDialogResultListener.BUTTON_POSITIVE
import eltos.simpledialogfragment.color.SimpleColorDialog

class PerAppSettingsFragment : Fragment(), SimpleDialog.OnDialogResultListener {
    private val apps = ArrayList<ProgressBarApp>()
    private val appsRepository by lazy { ProgressBarAppsRepository.getInstance(requireContext()) }
    private val recyclerView by lazy { requireView().findViewById<RecyclerView>(R.id.apps_recycler_view) }
    private val packageManager by lazy { requireContext().packageManager }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_per_app_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateAppsFromDatabase()

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = ProgressBarAppsAdapter(this, requireContext(), apps)

        updateRecyclerViewVisibility()
    }

    override fun onResult(dialogTag: String, which: Int, extras: Bundle): Boolean {
        if (which == BUTTON_POSITIVE || which == BUTTON_NEUTRAL) {
            var color = extras.getInt(SimpleColorDialog.COLOR)
            if (which == BUTTON_NEUTRAL || color == PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getInt(
                    "progressBarColor",
                    ContextCompat.getColor(requireContext(), R.color.purple_500)
                )
            ) {
                color = 1
            } else if (Build.VERSION.SDK_INT >= VERSION_CODES.S && color == ContextCompat.getColor(
                    requireContext(), R.color.system_accent_color
                ) && extras.getInt(SimpleColorDialog.SELECTED_SINGLE_POSITION) != 19
            ) {
                color = 2
            }
            if (apps[dialogTag.toInt()].color != color) {
                apps[dialogTag.toInt()].color = color
                appsRepository.updateApp(apps[dialogTag.toInt()])
                recyclerView.adapter?.notifyItemChanged(dialogTag.toInt())
            }
        }
        return true
    }

    private fun updateAppsFromDatabase() {
        apps.clear()
        apps.addAll(appsRepository.getAll())
        removeUninstalledApps()
        alphabetizeApps()
    }

    private fun removeUninstalledApps() {
        apps.removeAll { app ->
            try {
                packageManager.getApplicationInfo(app.packageName, 0)
                false
            } catch (e: Exception) {
                true
            }
        }
    }

    private fun alphabetizeApps() {
        apps.sortBy {
            try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(
                        it.packageName, 0
                    )
                ).toString().lowercase()
            } catch (e: Exception) {
                it.packageName
            }
        }
    }

    private fun updateRecyclerViewVisibility() {
        val emptyRecyclerView = requireView().findViewById<TextView>(R.id.empty_view)
        if (apps.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyRecyclerView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyRecyclerView.visibility = View.GONE
        }
    }
}