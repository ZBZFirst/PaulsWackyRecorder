package com.example.templei.feature.screen4

import java.util.Locale

enum class Screen4NumericKind {
    INTEGER,
    DECIMAL,
}

data class Screen4NumericFormatPolicy(
    val numericKind: Screen4NumericKind,
    val maxDigits: Int,
    val decimalPlaces: Int,
    val useSeparators: Boolean,
    val allowNegative: Boolean,
) {
    private fun encodedValue(): String {
        return listOf(
            numericKind.name,
            maxDigits.toString(),
            decimalPlaces.toString(),
            useSeparators.toString(),
            allowNegative.toString(),
        ).joinToString("|")
    }

    fun constraintType(): String =
        if (numericKind == Screen4NumericKind.INTEGER) "integer" else "decimal"

    fun displayLabel(): String =
        if (numericKind == Screen4NumericKind.INTEGER) "Integer" else "Decimal"

    fun summary(): String {
        val pieces = mutableListOf<String>()
        pieces += "max digits $maxDigits"
        if (numericKind == Screen4NumericKind.DECIMAL) {
            pieces += "decimal places $decimalPlaces"
        }
        pieces += if (useSeparators) "use separators" else "no separators"
        pieces += if (allowNegative) "allow negative" else "positive only"
        return pieces.joinToString(", ")
    }

    fun inputMaxLength(): Int {
        val signCharacters = if (allowNegative) 1 else 0
        val separatorCharacters = if (useSeparators) ((maxDigits - 1).coerceAtLeast(0) / 3) else 0
        val decimalCharacters = if (numericKind == Screen4NumericKind.DECIMAL && decimalPlaces > 0) 1 + decimalPlaces else 0
        return maxDigits + signCharacters + separatorCharacters + decimalCharacters
    }

    fun toFakerKey(): String {
        return PREFIX + encodedValue()
    }

    companion object {
        private const val PREFIX = "screen4.numeric_policy."
        private const val CONFIG_SEPARATOR = "::config|"

        fun normalizedTemplateFakerKey(fakerKey: String): String =
            fakerKey.substringBefore(CONFIG_SEPARATOR)

        fun fromFakerKey(fakerKey: String): Screen4NumericFormatPolicy? {
            val normalizedKey = normalizedTemplateFakerKey(fakerKey)
            if (!normalizedKey.startsWith(PREFIX)) return null
            val parts = normalizedKey.removePrefix(PREFIX).split("|")
            if (parts.size != 5) return null

            val numericKind = runCatching { Screen4NumericKind.valueOf(parts[0].uppercase(Locale.US)) }.getOrNull()
                ?: return null
            val maxDigits = parts[1].toIntOrNull() ?: return null
            val decimalPlaces = parts[2].toIntOrNull() ?: return null
            val useSeparators = parts[3].toBooleanStrictOrNull() ?: return null
            val allowNegative = parts[4].toBooleanStrictOrNull() ?: return null

            return Screen4NumericFormatPolicy(
                numericKind = numericKind,
                maxDigits = maxDigits,
                decimalPlaces = decimalPlaces,
                useSeparators = useSeparators,
                allowNegative = allowNegative,
            )
        }
    }
}

data class Screen4SimpleNumericOption(
    val key: String,
    val label: String,
    val numericKind: Screen4NumericKind,
)

object Screen4SimpleNumericCatalog {
    val options: List<Screen4SimpleNumericOption> = listOf(
        Screen4SimpleNumericOption(
            key = "integer",
            label = "Integer",
            numericKind = Screen4NumericKind.INTEGER,
        ),
        Screen4SimpleNumericOption(
            key = "decimal",
            label = "Decimal",
            numericKind = Screen4NumericKind.DECIMAL,
        ),
    )
}
