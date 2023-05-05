package com.example.testapplication.utils

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog

class EnumDialog(private val context: Context, private val title: Spanned, private val variants: Array<String>, private val value: Int) {

    private var func: ((Int) -> Unit)? = null
    private var onDismissListener: (() -> Unit)? = null


    constructor(context: Context, title: String, variants: Array<String>, value: Int)
            : this(context, SpannableString(title), variants, value)


    fun setOnSelectListener(func: (Int) -> Unit): EnumDialog {
        this.func = func
        return this
    }

    fun setOnDismissListener(func: (() -> Unit)): EnumDialog {
        this.onDismissListener = func
        return this
    }


    fun show() {
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, android.R.id.text1, variants)

        AlertDialog.Builder(context)
                .setTitle(title)
                .setAdapter(adapter) { d, w -> func?.invoke(w); d.dismiss() }
                .setOnDismissListener {onDismissListener?.invoke() }
                .show()
    }
}
