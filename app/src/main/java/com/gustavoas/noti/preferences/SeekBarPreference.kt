package com.gustavoas.noti.preferences

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.SeekBar
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.gustavoas.noti.R

class SeekBarPreference(context: Context, attrs: AttributeSet): Preference(context, attrs) {
    private var preferenceHolder: View? = null
    private var seekBar: SeekBar? = null
    private var editText: EditText? = null
    init {
        layoutResource = R.layout.seekbar_preference
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        preferenceHolder = holder.itemView
        seekBar = preferenceHolder?.findViewById(R.id.seekbar)
        editText = preferenceHolder?.findViewById(R.id.edittext)

        val currentValue = getPersistedInt(70)
        seekBar?.progress = currentValue.div(10)
        editText?.setText((currentValue.plus(10)).toString())
        editText?.setSelection(editText?.text?.length ?: 0)

        seekBar?.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    editText?.setText((progress.plus(1).times(10)).toString())
                    editText?.setSelection(editText?.text?.length ?: 0)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        editText?.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val value = s.toString().toIntOrNull()?.minus(10) ?: 0
                seekBar?.progress = value.div(10)
                persistInt(value)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        preferenceHolder?.setOnClickListener {
            editText?.requestFocus()
            imm.showSoftInput(editText, InputMethodManager.SHOW_FORCED)
        }

        editText?.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                editText?.clearFocus()
                imm.hideSoftInputFromWindow(editText?.windowToken, 0)
            }
            false
        }
    }
}