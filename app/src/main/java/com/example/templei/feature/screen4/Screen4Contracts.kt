package com.example.templei.feature.screen4

/**
 * Phase 1 typed-contract foundation for Screen 4.
 *
 * These contracts are intentionally lightweight and deterministic so later phases
 * (registry, validators, widget mapping) can attach behavior without redefining core types.
 */
enum class Screen4PrimitiveType {
    STRING,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    TIMESTAMP,
}

enum class Screen4ColumnCategory {
    IDENTITY,
    CONTACT,
    INTERNET,
    FINANCIAL,
    GEOGRAPHIC,
    TEXT,
    FILE,
    COLOR,
    DATE,
    TIME,
    NUMBER,
    FORMAT,
    CUSTOM,
}

data class ColumnTypeDefinition(
    val name: String,
    val category: Screen4ColumnCategory,
    val primitiveType: Screen4PrimitiveType,
    val validatorKey: String,
    val uiWidget: String,
)

data class ColumnDefinition(
    val name: String,
    val columnType: String,
    val nullable: Boolean,
)

data class TableSchema(
    val tableName: String,
    val columns: List<ColumnDefinition>,
)
