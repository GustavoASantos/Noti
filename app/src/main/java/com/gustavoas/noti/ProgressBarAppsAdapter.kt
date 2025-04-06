package com.gustavoas.noti

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.gustavoas.noti.Utils.dpToPx
import com.gustavoas.noti.Utils.getApplicationIcon
import com.gustavoas.noti.Utils.getApplicationName
import com.gustavoas.noti.Utils.showColorDialog
import com.gustavoas.noti.model.ProgressBarApp
import com.gustavoas.noti.services.AccessibilityService

class ProgressBarAppsAdapter(
    private val fragment: Fragment,
    private val context: Context,
    private val apps: ArrayList<ProgressBarApp>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val VIEW_TYPE_HEADER = 0
    private val VIEW_TYPE_ITEM = 1
    private val VIEW_TYPE_FOOTER = 2

    private val appsRepository by lazy { ProgressBarAppsRepository.getInstance(context) }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> VIEW_TYPE_HEADER
            itemCount - 1 -> VIEW_TYPE_FOOTER
            else -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(context).inflate(R.layout.button_toggle_group, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_FOOTER -> {
                val view = LayoutInflater.from(context).inflate(R.layout.per_app_settings_footer, parent, false)
                FooterViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(context).inflate(R.layout.app_item, parent, false)
                ItemViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder !is ItemViewHolder) return

        with(apps[position - 1]) {
            holder.appName.text = getApplicationName(context, packageName) ?: packageName
            val appIcon = getApplicationIcon(context, packageName) ?: ColorDrawable(Color.TRANSPARENT)
            val appIconSize = dpToPx(context, 36)
            appIcon.setBounds(0, 0, appIconSize, appIconSize)
            holder.appName.setCompoundDrawables(appIcon, null, null, null)
            holder.toggle.isChecked = showProgressBar
            holder.toggle.setOnCheckedChangeListener { _, isChecked ->
                showProgressBar = isChecked
                appsRepository.updateApp(this)
                if (!isChecked) {
                    val intent = Intent(context, AccessibilityService::class.java)
                    intent.putExtra("packageName", packageName)
                    intent.putExtra("removal", true)
                    context.startService(intent)
                }
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
                showColorDialog(
                    fragment,
                    barColor,
                    (position - 1).toString(),
                    color != 1
                )
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)

        if (holder !is ItemViewHolder) return
        holder.toggle.setOnCheckedChangeListener(null)
    }

    override fun getItemCount(): Int = apps.size + 2

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val toggleGroup: MaterialButtonToggleGroup = itemView.findViewById(R.id.toggleGroup)

        init {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.itemView.context)
            val enableDownloads = sharedPreferences.getBoolean("showForDownloads", true)
            val enableMedia = sharedPreferences.getBoolean("showForMedia", true)

            if (enableDownloads) {
                toggleGroup.check(R.id.toggleDownloads)
            }

            if (enableMedia) {
                toggleGroup.check(R.id.toggleMedia)
            }

            toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                when (checkedId) {
                    R.id.toggleDownloads -> {
                        sharedPreferences.edit().putBoolean("showForDownloads", isChecked).apply()
                    }
                    R.id.toggleMedia -> {
                        sharedPreferences.edit().putBoolean("showForMedia", isChecked).apply()
                    }
                }
            }
        }
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appName: TextView = view.findViewById(R.id.app_name)
        val toggle: CheckBox = view.findViewById(R.id.checkbox)
        val background: LinearLayout = view.findViewById(R.id.item_container)
        val colorPicker: Button = view.findViewById(R.id.color_picker)
    }

    class FooterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}