package com.gustavoas.noti.preferences

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import android.widget.ImageButton
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.gustavoas.noti.R

class BannerPreference(context: Context, attrs: AttributeSet): Preference(context, attrs) {

    var onBtnClick = { intent?.let { context.startActivity(intent) } }

    init {
        layoutResource = R.layout.banner_preference
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val persistedValue = getPersistedBoolean(true)
        val preferenceHolder = holder.itemView

        if (!persistedValue) {
            this.isVisible = false
            return
        }

        val btn = preferenceHolder.findViewById<Button>(R.id.bannerBtn)
        btn.text = summary

        btn.setOnClickListener {
            onBtnClick()
        }

        val closeBtn = preferenceHolder.findViewById<ImageButton>(R.id.closeBtn)

        closeBtn.setOnClickListener {
            this.isVisible = false
            persistBoolean(false)
        }
    }
}