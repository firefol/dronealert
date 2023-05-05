package com.example.testapplication.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.example.testapplication.R
import com.example.testapplication.utils.EnumDialog

class ViewAdapter() : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Item>()

    operator fun plusAssign(item: Item) {
        items += item
        notifyItemInserted(items.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == -1)
            throw IllegalStateException("viewType not found")

        val inflater = LayoutInflater.from(parent.context)
        return object : RecyclerView.ViewHolder(inflater.inflate(viewType, parent, false)) {}
    }

    @SuppressLint("CutPasteId")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            R.layout.adapter_view_item_button -> {
                val dataItem = (items[position] as DataItem)

                holder.itemView.findViewById<TextView>(R.id.nameView).apply {
                    text = makeTitle(holder.itemView.context, dataItem)
                    if (!dataItem.isReadOnly) {
                        setOnClickListener { (dataItem.data as ButtonData).callback.invoke() }
                    } else {
                        setOnClickListener {
                            // do nothing
                        }
                    }
                }
            }
            R.layout.adapter_view_item_enum -> {
                val dataItem = (items[position] as DataItem)
                val enumData = dataItem.data as EnumEditData

                val name = makeTitle(holder.itemView.context, dataItem)

                holder.itemView.findViewById<TextView>(R.id.nameView).apply {
                    text = name
                }

                val i = enumData.value

                holder.itemView.findViewById<TextView>(R.id.valueView).text =
                    enumData.variants.getOrNull(i) ?: "Не выбрано"

                if (!dataItem.isReadOnly) {
                    holder.itemView.findViewById<View>(R.id.enumLayout).setOnClickListener {
                        val dialog = EnumDialog(
                            context = holder.itemView.context,
                            title = name,
                            variants = enumData.variants,
                            value = enumData.value)

                        dialog.setOnSelectListener { value ->
                            editItemById(dataItem.id) {
                                enumData.value = value
                            }
                        }

                        dialog.show()
                    }
                } else {
                    holder.itemView.findViewById<View>(R.id.enumLayout).setOnClickListener {
                        // do nothing
                    }
                }
            }
            R.layout.adapter_view_item_text,  R.layout.adapter_view_item_text_horizontal -> {
                val dataItem = (items[position] as DataItem)
                val textData = dataItem.data as TextEditData

                val name = makeTitle(holder.itemView.context, dataItem)

                holder.itemView.findViewById<TextView>(R.id.nameView).apply {
                    text = name
                }

                val valueView = holder.itemView.findViewById<TextView>(R.id.valueView)

                valueView.text = textData.value ?: "Не выбрано"
                holder.itemView.findViewById<View>(R.id.textLayout).setOnClickListener {
                        // do nothing
                }
            }

        }
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            items[position] is GroupItem -> R.layout.adapter_view_item_group
            items[position] is DataItem -> with((items[position] as DataItem).data) {
                when (this) {
                    is TextEditData -> if ((this as? TextEditData)?.isHorizontal == true) {
                        R.layout.adapter_view_item_text_horizontal
                    } else {
                        R.layout.adapter_view_item_text
                    }
                    is EnumEditData -> R.layout.adapter_view_item_enum
                    else -> -1
                }
            }
            else -> -1
        }
    }

    override fun getItemCount(): Int = items.size

    fun editItemById(id: Int, function: (item: Item) -> Unit) {
        val i = items.indexOfFirst {
            (it is DataItem && it.id == id) || (it is DiagnosticsItem && it.id == id) || (it is FractionItem && it.id == id)
        }

        if (i != -1) {
            function(items[i])
            notifyItemChanged(i)
        }
    }

    fun getItemById(id: Int): Item? {
        return items.find { (it is DataItem && it.id == id) || (it is DiagnosticsItem && it.id == id) }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun makeTitle(context: Context, dataItem: DataItem, addText: String = ""): Spanned {
        val text = dataItem.name + addText
        val textAsterisk = SpannableString("$text *")
        val span = ForegroundColorSpan(context.getColor(R.color.teal_200))
        textAsterisk.setSpan(span,
            text.length + 1,
            text.length + 2,
            Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
        return if (dataItem.base) textAsterisk else SpannableString(text)
    }


    interface Item

    interface Data

    data class GroupItem(
        var name: String,
        var topMargin: Boolean
    ) : Item

    data class DiagnosticsItem(
        var id: Int,
        var name: String,
        var text: String = "",
        var icon: Int = 0,
        var state: State = State.Failure
    ) : Item {
        enum class State {
            Success,
            Warning,
            NearFailure,
            Failure
        }
    }

    data class DataItem(
        var id: Int,
        var name: String,
        var data: Data,
        var base: Boolean = false,
        var isReadOnly: Boolean = false
    ) : Item

    data class FractionItem(
        var id: Int,
        var fractionPercent: Float = 0.0F,
        var firstText: String = "",
        var secondText: String = ""
    ) : Item

    data class ButtonData(
        var callback: () -> Unit
    ) : Data

    data class TextEditData(
        var value: String?,
        var canEditFunc: (String, String) -> Boolean = { _, _ -> true },
        var inputType: Int = InputType.TYPE_CLASS_TEXT,
        var isHorizontal: Boolean = false
    ) : Data

    data class EnumEditData(
        var variants: Array<String>,
        var value: Int
    ) : Data {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EnumEditData

            if (!variants.contentEquals(other.variants)) return false
            if (value != other.value) return false

            return true
        }

        override fun hashCode(): Int {
            var result = variants.contentHashCode()
            result = 31 * result + value
            return result
        }
    }

    data class BoolEditData(
        var value: Boolean,
        var description: String
    ) : Data
}