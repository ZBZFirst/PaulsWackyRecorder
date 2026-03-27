package com.example.templei.feature.screen4

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.widget.AutoCompleteTextView
import android.widget.EditText
import com.example.templei.R

/**
 * UI policy for Screen 4 inputs backed by workbook metadata and semantic type definitions.
 */
object Screen4InputUiPolicy {
    fun buildHint(context: Context, column: ActiveColumn): String {
        column.numericPolicy?.let { policy ->
            return if (policy.numericKind == Screen4NumericKind.INTEGER) {
                context.getString(R.string.screen4_hint_integer)
            } else {
                context.getString(R.string.screen4_hint_decimal)
            }
        }

        val resolvedType = Screen4ColumnTypeRegistry.resolveByConstraintType(column.constraintType)
        return when {
            usesAllowedValuePicker(column) -> context.getString(R.string.screen4_hint_list)
            usesTimestampPicker(column, resolvedType) -> context.getString(R.string.screen4_hint_timestamp)
            usesDatePicker(column, resolvedType) -> context.getString(R.string.screen4_hint_date)
            usesTimePicker(column, resolvedType) -> context.getString(R.string.screen4_hint_time)
            prefersMultilineInput(column) -> context.getString(R.string.screen4_hint_long_text)
            resolvedType.uiWidget == "EmailInput" -> context.getString(R.string.screen4_hint_email)
            resolvedType.uiWidget == "PhoneInput" -> context.getString(R.string.screen4_hint_phone)
            resolvedType.uiWidget == "NumericInput" -> context.getString(R.string.screen4_hint_integer)
            resolvedType.uiWidget == "DecimalInput" -> context.getString(R.string.screen4_hint_decimal)
            else -> context.getString(R.string.screen4_hint_text)
        }
    }

    fun usesAllowedValuePicker(column: ActiveColumn): Boolean {
        return column.metadata?.allowedValues?.isNotEmpty() == true
    }

    fun allowedValues(column: ActiveColumn): List<String> = column.metadata?.allowedValues.orEmpty()

    fun applyAllowedValuePickerBehavior(input: AutoCompleteTextView) {
        applyPickerFieldBehavior(input)
        input.setOnClickListener { input.showDropDown() }
    }

    fun applyPickerFieldBehavior(input: EditText) {
        input.showSoftInputOnFocus = false
        input.keyListener = null
        input.inputType = InputType.TYPE_NULL
        input.isCursorVisible = false
        input.isLongClickable = false
        input.setTextIsSelectable(false)
    }

    fun usesDatePicker(column: ActiveColumn, resolvedType: ColumnTypeDefinition): Boolean {
        return resolvedType.uiWidget == "DateInput" || column.constraintType.startsWith("date_")
    }

    fun prefersCompactDateEntry(column: ActiveColumn, resolvedType: ColumnTypeDefinition): Boolean {
        return usesDatePicker(column, resolvedType) &&
            column.metadata?.uiInputType?.uppercase() == "NUMPAD"
    }

    fun usesTimePicker(column: ActiveColumn, resolvedType: ColumnTypeDefinition): Boolean {
        return resolvedType.uiWidget == "TimeInput" &&
            !column.constraintType.startsWith("timestamp_")
    }

    fun usesTimestampPicker(column: ActiveColumn, resolvedType: ColumnTypeDefinition): Boolean {
        return Screen4TemporalInputPolicy.usesTimestampPicker(column, resolvedType)
    }

    fun shouldApplyLiveFormatter(column: ActiveColumn, resolvedType: ColumnTypeDefinition): Boolean {
        return !prefersCompactDateEntry(column, resolvedType)
    }

    fun prefersMultilineInput(column: ActiveColumn): Boolean {
        return column.maxLength >= 120 || column.metadata?.group == "TEXT"
    }

    fun applyToInput(input: EditText, column: ActiveColumn, resolvedType: ColumnTypeDefinition) {
        input.filters = arrayOf(InputFilter.LengthFilter(column.maxLength))
        column.numericPolicy?.let { policy ->
            if (policy.numericKind == Screen4NumericKind.INTEGER) {
                input.inputType = InputType.TYPE_CLASS_NUMBER or
                    if (policy.allowNegative) InputType.TYPE_NUMBER_FLAG_SIGNED else 0
                input.keyListener = if (policy.useSeparators) {
                    DigitsKeyListener.getInstance(if (policy.allowNegative) "0123456789,-" else "0123456789,")
                } else {
                    DigitsKeyListener.getInstance(if (policy.allowNegative) "0123456789-" else "0123456789")
                }
            } else {
                input.inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    if (policy.allowNegative) InputType.TYPE_NUMBER_FLAG_SIGNED else 0
                input.keyListener = if (policy.useSeparators) {
                    DigitsKeyListener.getInstance(if (policy.allowNegative) "0123456789,.-" else "0123456789,.")
                } else {
                    DigitsKeyListener.getInstance(if (policy.allowNegative) "0123456789.-" else "0123456789.")
                }
            }
            return
        }

        when {
            column.metadata?.uiInputType == "NUMPAD" && resolvedType.primitiveType == Screen4PrimitiveType.INTEGER -> {
                input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                input.keyListener = DigitsKeyListener.getInstance("0123456789-")
            }

            column.metadata?.uiInputType == "NUMPAD" && resolvedType.primitiveType == Screen4PrimitiveType.DECIMAL -> {
                input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                input.keyListener = DigitsKeyListener.getInstance("0123456789.-")
            }

            else -> {
                input.inputType = inputTypeForWidget(resolvedType.uiWidget)
            }
        }

        if (prefersMultilineInput(column)) {
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            input.minLines = 3
            input.maxLines = 5
            input.isSingleLine = false
            input.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        }
    }

    private fun inputTypeForWidget(widget: String): Int {
        return when (widget) {
            "NumericInput" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            "DecimalInput" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            "EmailInput" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            "PhoneInput" -> InputType.TYPE_CLASS_PHONE
            "DateInput" -> InputType.TYPE_CLASS_DATETIME
            "TimeInput" -> InputType.TYPE_CLASS_DATETIME
            "TimestampInput" -> InputType.TYPE_CLASS_NUMBER
            else -> InputType.TYPE_CLASS_TEXT
        }
    }
}
