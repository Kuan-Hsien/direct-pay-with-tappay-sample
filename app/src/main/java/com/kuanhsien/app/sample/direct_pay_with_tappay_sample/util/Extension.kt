package com.kuanhsien.app.sample.direct_pay_with_tappay_sample.util

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

fun EditText.afterTextChanged(afterTextChangedHandler: EditText.(oldText: String, newText: String, isDeleted: Boolean) -> Unit) {

    val editText = this

    addTextChangedListener(object : TextWatcher {

        private var oldText: String = emptyString()

        override fun afterTextChanged(s: Editable?) {
            editText.apply {
                val newText = s.toString()
                afterTextChangedHandler(oldText, newText, newText.length < oldText.length)
            }
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            oldText = s.toString()
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

        }
    })

}

fun emptyString() = ""