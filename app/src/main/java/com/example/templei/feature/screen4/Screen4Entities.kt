package com.example.templei.feature.screen4

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "column_templates",
    indices = [Index(value = ["fakerKey"], unique = true)]
)
data class ColumnTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fakerKey: String,
    val defaultLabel: String,
    val constraintType: String,
    val maxLength: Int,
    val required: Boolean,
)

@Entity(
    tableName = "columns",
    foreignKeys = [
        ForeignKey(
            entity = ColumnTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["templateId"]), Index(value = ["position"], unique = true)]
)
data class ColumnEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: Long,
    val position: Int,
    val label: String,
    val isActive: Boolean = true,
)

@Entity(
    tableName = "rows",
    foreignKeys = [
        ForeignKey(
            entity = ColumnTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["createdAtMillis"]), Index(value = ["templateId"])]
)
data class RowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: Long,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "cells",
    foreignKeys = [
        ForeignKey(
            entity = RowEntity::class,
            parentColumns = ["id"],
            childColumns = ["rowId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ColumnEntity::class,
            parentColumns = ["id"],
            childColumns = ["columnId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ColumnTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["rowId"]),
        Index(value = ["columnId"]),
        Index(value = ["templateId"]),
        Index(value = ["rowId", "columnId"], unique = true),
    ],
)
data class CellEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rowId: Long,
    val columnId: Long,
    val templateId: Long,
    @ColumnInfo(defaultValue = "")
    val value: String,
)
