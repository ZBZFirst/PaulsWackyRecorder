package com.example.templei.feature.screen4

/**
 * Phase 6 registry baseline: all 107 semantic types are declared here.
 *
 * This provides deterministic type names, categories, primitive types, validator keys,
 * and widget mappings for the complete checklist. Validator depth can continue to evolve
 * while preserving stable type identities.
 */
object Screen4ColumnTypeRegistry {
    private fun def(
        name: String,
        category: Screen4ColumnCategory,
        primitiveType: Screen4PrimitiveType,
        validatorKey: String,
        uiWidget: String,
    ) = ColumnTypeDefinition(
        name = name,
        category = category,
        primitiveType = primitiveType,
        validatorKey = validatorKey,
        uiWidget = uiWidget,
    )

    private val definitions: List<ColumnTypeDefinition> = listOf(
        // Identity / Person
        def("uuid4", Screen4ColumnCategory.IDENTITY, Screen4PrimitiveType.STRING, "uuid4", "TextInput"),
        def("name", Screen4ColumnCategory.IDENTITY, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("first_name", Screen4ColumnCategory.IDENTITY, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("last_name", Screen4ColumnCategory.IDENTITY, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("prefix", Screen4ColumnCategory.IDENTITY, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("suffix", Screen4ColumnCategory.IDENTITY, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("user_name", Screen4ColumnCategory.IDENTITY, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("job", Screen4ColumnCategory.IDENTITY, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("company", Screen4ColumnCategory.IDENTITY, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("company_suffix", Screen4ColumnCategory.IDENTITY, Screen4PrimitiveType.STRING, "text", "TextInput"),

        // Contact / Address
        def("email", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "email", "EmailInput"),
        def("ascii_email", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "email", "EmailInput"),
        def("company_email", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "email", "EmailInput"),
        def("phone_number", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "phone", "PhoneInput"),
        def("basic_phone_number", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "phone", "PhoneInput"),
        def("address", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("street_address", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("street_name", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("building_number", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("city", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("city_prefix", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("city_suffix", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("state", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("state_abbr", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("country", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("country_code", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("zipcode", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("postalcode", Screen4ColumnCategory.CONTACT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("timezone", Screen4ColumnCategory.TIME, Screen4PrimitiveType.STRING, "text", "TextInput"),

        // Internet / Network
        def("domain_name", Screen4ColumnCategory.INTERNET, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("domain_word", Screen4ColumnCategory.INTERNET, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("url", Screen4ColumnCategory.INTERNET, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("uri", Screen4ColumnCategory.INTERNET, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("uri_path", Screen4ColumnCategory.INTERNET, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("uri_page", Screen4ColumnCategory.INTERNET, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("uri_extension", Screen4ColumnCategory.INTERNET, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("ipv4", Screen4ColumnCategory.INTERNET, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("ipv6", Screen4ColumnCategory.INTERNET, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("mac_address", Screen4ColumnCategory.INTERNET, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("hostname", Screen4ColumnCategory.INTERNET, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("user_agent", Screen4ColumnCategory.INTERNET, Screen4PrimitiveType.STRING, "text", "TextInput"),

        // Financial
        def("credit_card_number", Screen4ColumnCategory.FINANCIAL, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("credit_card_provider", Screen4ColumnCategory.FINANCIAL, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("credit_card_expire", Screen4ColumnCategory.FINANCIAL, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("credit_card_security_code", Screen4ColumnCategory.FINANCIAL, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("currency_code", Screen4ColumnCategory.FINANCIAL, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("currency_name", Screen4ColumnCategory.FINANCIAL, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("currency_symbol", Screen4ColumnCategory.FINANCIAL, Screen4PrimitiveType.STRING, "text", "TextInput"),

        // Geographic
        def("latitude", Screen4ColumnCategory.GEOGRAPHIC, Screen4PrimitiveType.DECIMAL, "decimal", "DecimalInput"),
        def("longitude", Screen4ColumnCategory.GEOGRAPHIC, Screen4PrimitiveType.DECIMAL, "decimal", "DecimalInput"),
        def("coordinate", Screen4ColumnCategory.GEOGRAPHIC, Screen4PrimitiveType.STRING, "text", "TextInput"),

        // Text / Language
        def("word", Screen4ColumnCategory.TEXT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("words", Screen4ColumnCategory.TEXT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("sentence", Screen4ColumnCategory.TEXT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("paragraph", Screen4ColumnCategory.TEXT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("slug", Screen4ColumnCategory.TEXT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("text", Screen4ColumnCategory.TEXT, Screen4PrimitiveType.STRING, "text", "TextInput"),

        // File / Media
        def("mime_type", Screen4ColumnCategory.FILE, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("file_name", Screen4ColumnCategory.FILE, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("file_extension", Screen4ColumnCategory.FILE, Screen4PrimitiveType.STRING, "text", "TextInput"),

        // Color
        def("color_name", Screen4ColumnCategory.COLOR, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("hex_color", Screen4ColumnCategory.COLOR, Screen4PrimitiveType.STRING, "text", "TextInput"),

        // Date Formats
        def("date_mdy_slash_yyyy", Screen4ColumnCategory.DATE, Screen4PrimitiveType.STRING, "date", "DateInput"),
        def("date_mdy_dash_yyyy", Screen4ColumnCategory.DATE, Screen4PrimitiveType.STRING, "date", "DateInput"),
        def("date_mdy_slash_yy", Screen4ColumnCategory.DATE, Screen4PrimitiveType.STRING, "date", "DateInput"),
        def("date_mdy_dash_yy", Screen4ColumnCategory.DATE, Screen4PrimitiveType.STRING, "date", "DateInput"),

        // Timestamp Formats
        def("timestamp_mdy_slash_minute", Screen4ColumnCategory.TIME, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("timestamp_mdy_dash_minute", Screen4ColumnCategory.TIME, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("timestamp_mdy_slash_second", Screen4ColumnCategory.TIME, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("timestamp_mdy_dash_second", Screen4ColumnCategory.TIME, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("timestamp_mdy_slash_millisecond", Screen4ColumnCategory.TIME, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("timestamp_mdy_dash_millisecond", Screen4ColumnCategory.TIME, Screen4PrimitiveType.STRING, "text", "TextInput"),

        // Time Formats
        def("time_hh_mm", Screen4ColumnCategory.TIME, Screen4PrimitiveType.STRING, "time", "TimeInput"),
        def("time_hh_mm_ss", Screen4ColumnCategory.TIME, Screen4PrimitiveType.STRING, "time", "TimeInput"),
        def("time_hh_mm_ss_mmm", Screen4ColumnCategory.TIME, Screen4PrimitiveType.STRING, "time", "TimeInput"),
        def("time_hh_mm_am_pm", Screen4ColumnCategory.TIME, Screen4PrimitiveType.STRING, "time", "TimeInput"),
        def("time_hh_mm_ss_am_pm", Screen4ColumnCategory.TIME, Screen4PrimitiveType.STRING, "time", "TimeInput"),
        def("time_hh_mm_ss_mmm_am_pm", Screen4ColumnCategory.TIME, Screen4PrimitiveType.STRING, "time", "TimeInput"),

        // Unix Time
        def("timestamp_unix_s", Screen4ColumnCategory.TIME, Screen4PrimitiveType.INTEGER, "integer", "NumericInput"),
        def("timestamp_unix_ms", Screen4ColumnCategory.TIME, Screen4PrimitiveType.TIMESTAMP, "timestamp_unix_ms", "TimestampInput"),

        // Date Components
        def("year_yyyy", Screen4ColumnCategory.DATE, Screen4PrimitiveType.INTEGER, "integer", "NumericInput"),
        def("year_yy", Screen4ColumnCategory.DATE, Screen4PrimitiveType.INTEGER, "integer", "NumericInput"),
        def("month_mm", Screen4ColumnCategory.DATE, Screen4PrimitiveType.INTEGER, "integer", "NumericInput"),
        def("day_dd", Screen4ColumnCategory.DATE, Screen4PrimitiveType.INTEGER, "integer", "NumericInput"),
        def("day_of_week", Screen4ColumnCategory.DATE, Screen4PrimitiveType.STRING, "text", "TextInput"),

        // Numeric Formats
        def("integer", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.INTEGER, "integer", "NumericInput"),
        def("decimal", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.DECIMAL, "decimal", "DecimalInput"),
        def("decimal_1", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.DECIMAL, "decimal", "DecimalInput"),
        def("decimal_2", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.DECIMAL, "decimal", "DecimalInput"),
        def("decimal_3", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.DECIMAL, "decimal", "DecimalInput"),
        def("decimal_grouped_2", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.DECIMAL, "decimal", "DecimalInput"),
        def("ones", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.INTEGER, "integer", "NumericInput"),
        def("tens", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.INTEGER, "integer", "NumericInput"),
        def("hundreds", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.INTEGER, "integer", "NumericInput"),
        def("thousands", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.INTEGER, "integer", "NumericInput"),
        def("ten_thousands", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.INTEGER, "integer", "NumericInput"),
        def("hundred_thousands", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.INTEGER, "integer", "NumericInput"),
        def("millions", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.INTEGER, "integer", "NumericInput"),
        def("number_plain", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.INTEGER, "integer", "NumericInput"),
        def("number_grouped", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.INTEGER, "integer", "NumericInput"),
        def("number_scientific", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.DECIMAL, "decimal", "DecimalInput"),
        def("currency_usd", Screen4ColumnCategory.FINANCIAL, Screen4PrimitiveType.DECIMAL, "decimal", "DecimalInput"),
        def("currency_usd_plain", Screen4ColumnCategory.FINANCIAL, Screen4PrimitiveType.DECIMAL, "decimal", "DecimalInput"),
        def("percent", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.DECIMAL, "decimal", "DecimalInput"),
        def("percent_decimal", Screen4ColumnCategory.NUMBER, Screen4PrimitiveType.DECIMAL, "decimal", "DecimalInput"),

        // Formatting Tokens
        def("date_separator_slash", Screen4ColumnCategory.FORMAT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("date_separator_dash", Screen4ColumnCategory.FORMAT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("grouping_separator", Screen4ColumnCategory.FORMAT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("decimal_separator", Screen4ColumnCategory.FORMAT, Screen4PrimitiveType.STRING, "text", "TextInput"),
        def("fraction_separator", Screen4ColumnCategory.FORMAT, Screen4PrimitiveType.STRING, "text", "TextInput"),
    )

    private val definitionsByName: Map<String, ColumnTypeDefinition> = definitions.associateBy { it.name }

    private val aliases: Map<String, String> = mapOf(
        "TEXT" to "text",
        "INTEGER" to "integer",
        "DECIMAL" to "decimal",
        "UUID4" to "uuid4",
        "EMAIL" to "email",
        "PHONE" to "phone_number",
        "DATE" to "date_mdy_dash_yyyy",
        "TIME" to "time_hh_mm_ss",
        "TIMESTAMP" to "timestamp_unix_ms",
    )

    fun resolveByConstraintType(constraintType: String): ColumnTypeDefinition {
        val key = aliases[constraintType.trim().uppercase()] ?: constraintType.trim().lowercase()
        return definitionsByName[key] ?: definitionsByName.getValue("text")
    }

    fun allDefinitions(): List<ColumnTypeDefinition> = definitions
}
