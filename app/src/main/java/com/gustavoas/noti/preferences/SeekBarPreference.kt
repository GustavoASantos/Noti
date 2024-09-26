package com.gustavoas.noti.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.Slider
import com.gustavoas.noti.R
import com.gustavoas.noti.Utils.vibrate

class SeekBarPreference(context: Context, attrs: AttributeSet): Preference(context, attrs) {
    private var preferenceHolder: View? = null

    private var minNumber: Int = -50
    private var maxNumber: Int = 50
    private var defaultValue: Int = 0

    private var currentValue: Int = defaultValue

    init {
        layoutResource = R.layout.seekbar_preference

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.HorizontalNumberPicker)
        minNumber = attributes.getInteger(R.styleable.HorizontalNumberPicker_min_number, minNumber)
        maxNumber = attributes.getInteger(R.styleable.HorizontalNumberPicker_max_number, maxNumber)
        defaultValue = attributes.getInteger(R.styleable.HorizontalNumberPicker_default_value, defaultValue)
        attributes.recycle()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        preferenceHolder = holder.itemView
        val seekBar = preferenceHolder?.findViewById<Slider>(R.id.slider)

        currentValue = getPersistedInt(defaultValue)
        seekBar?.value = currentValue.toFloat()
        seekBar?.valueFrom = minNumber.toFloat()
        seekBar?.valueTo = maxNumber.toFloat()

        seekBar?.addOnChangeListener { _, value, _ ->
            if (value.toInt() != currentValue) {
                currentValue = value.toInt()
                persistInt(currentValue)
                vibrate(context)
            }
        }
    }
}