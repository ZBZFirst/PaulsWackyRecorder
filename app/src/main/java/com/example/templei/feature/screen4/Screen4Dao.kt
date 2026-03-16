package com.example.templei.feature.screen4

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface Screen4Dao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTemplates(templates: List<ColumnTemplateEntity>): List<Long>

    @Query("SELECT * FROM column_templates ORDER BY id")
    suspend fun getTemplates(): List<ColumnTemplateEntity>


    @Insert
    suspend fun insertTemplate(template: ColumnTemplateEntity): Long

    @Query("SELECT * FROM column_templates WHERE constraintType = :constraintType LIMIT 1")
    suspend fun getTemplateByConstraintType(constraintType: String): ColumnTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertColumns(columns: List<ColumnEntity>): List<Long>

    @Insert
    suspend fun insertColumn(column: ColumnEntity): Long

    @Update
    suspend fun updateColumn(column: ColumnEntity)

    @Query("SELECT * FROM columns WHERE isActive = 1 ORDER BY position")
    suspend fun getActiveColumns(): List<ColumnEntity>

    @Query("UPDATE columns SET isActive = 0 WHERE id IN (:columnIds)")
    suspend fun deactivateColumns(columnIds: List<Long>)

    @Insert
    suspend fun insertRow(row: RowEntity): Long

    @Insert
    suspend fun insertCells(cells: List<CellEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCells(cells: List<CellEntity>)

    @Query("DELETE FROM rows WHERE id = :rowId")
    suspend fun deleteRow(rowId: Long)

    @Query("SELECT * FROM rows ORDER BY id DESC LIMIT 1")
    suspend fun getLatestRow(): RowEntity?

    @Query("DELETE FROM rows")
    suspend fun deleteAllRows()

    @Query("SELECT * FROM rows ORDER BY id DESC LIMIT :limit")
    suspend fun getRows(limit: Int): List<RowEntity>

    @Query("SELECT * FROM rows WHERE id = :rowId LIMIT 1")
    suspend fun getRowById(rowId: Long): RowEntity?

    @Query(
        """
        SELECT c.rowId, c.columnId, c.value
        FROM cells c
        WHERE c.rowId IN (:rowIds)
        """
    )
    suspend fun getCellsForRows(rowIds: List<Long>): List<RowCellRecord>

    @Query(
        """
        SELECT c.rowId, c.columnId, c.value
        FROM cells c
        WHERE c.rowId = :rowId
        """
    )
    suspend fun getCellsForRow(rowId: Long): List<RowCellRecord>

    @Query("SELECT COALESCE(MAX(position), -1) FROM columns")
    suspend fun getMaxColumnPosition(): Int

    @Query("SELECT * FROM columns WHERE id = :columnId LIMIT 1")
    suspend fun getColumnById(columnId: Long): ColumnEntity?
}

data class RowCellRecord(
    val rowId: Long,
    val columnId: Long,
    val value: String,
)
