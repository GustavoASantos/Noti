package com.gustavoas.noti.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gustavoas.noti.ProgressBarApp
import com.gustavoas.noti.ProgressBarAppsAdapter
import com.gustavoas.noti.ProgressBarAppsRepository
import com.gustavoas.noti.R

class PerAppSettingsFragment : Fragment() {
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
        recyclerView.adapter = ProgressBarAppsAdapter(requireContext(), apps)

        updateRecyclerViewVisibility()
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
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(it.packageName, 0)).toString()
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