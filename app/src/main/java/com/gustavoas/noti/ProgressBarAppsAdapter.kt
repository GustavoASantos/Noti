package com.gustavoas.noti

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView

class ProgressBarAppsAdapter(private val context: Context, private val apps: ArrayList<ProgressBarApp>): RecyclerView.Adapter<ProgressBarAppsAdapter.ViewHolder>() {
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
            val appIconSize = (36 * context.resources.displayMetrics.density).toInt()
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

    class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val appName: TextView = view.findViewById(R.id.app_name)
        val toggle: CheckBox = view.findViewById(R.id.checkbox)
        val background: LinearLayout = view.findViewById(R.id.item_container)
    }
}