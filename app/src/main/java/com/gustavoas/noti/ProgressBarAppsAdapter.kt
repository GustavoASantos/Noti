package com.gustavoas.noti

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.gustavoas.noti.Utils.dpToPx
import com.gustavoas.noti.Utils.showColorDialog
import com.gustavoas.noti.model.ProgressBarApp

class ProgressBarAppsAdapter(
    private val fragment: Fragment,
    private val context: Context,
    private val apps: ArrayList<ProgressBarApp>
) : RecyclerView.Adapter<ProgressBarAppsAdapter.ViewHolder>() {
    private val appsRepository by lazy { ProgressBarAppsRepository.getInstance(context) }
    private val packageManager by lazy { context.packageManager }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.app_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(apps[position]) {
            holder.appName.text = getAppName(packageName)
            val appIcon = getAppIcon(packageName)
            val appIconSize = dpToPx(context, 36)
            appIcon.setBounds(0, 0, appIconSize, appIconSize)
            holder.appName.setCompoundDrawables(appIcon, null, null, null)
            holder.toggle.isChecked = showProgressBar
            holder.toggle.setOnCheckedChangeListener { _, isChecked ->
                showProgressBar = isChecked
                appsRepository.updateApp(this)
            }
            holder.background.setOnClickListener {
                holder.toggle.toggle()
            }
            val barColor = when (color) {
                1 -> PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt("progressBarColor", ContextCompat.getColor(context, R.color.purple_500))

                2 -> ContextCompat.getColor(context, R.color.system_accent_color)
                else -> color
            }
            holder.colorPicker.setBackgroundColor(barColor)
            holder.colorPicker.setOnClickListener {
                showColorDialog(fragment, barColor, position.toString(), color != 1)
            }
        }
    }

    private fun getAppIcon(packageName: String): Drawable {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            ResourcesCompat.getDrawable(
                context.resources,
                R.drawable.ic_apps,
                null
            )!!
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    override fun getItemCount(): Int = apps.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appName: TextView = view.findViewById(R.id.app_name)
        val toggle: CheckBox = view.findViewById(R.id.checkbox)
        val background: LinearLayout = view.findViewById(R.id.item_container)
        val colorPicker: Button = view.findViewById(R.id.color_picker)
    }
}