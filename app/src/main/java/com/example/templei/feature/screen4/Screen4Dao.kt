package com.example.templei.feature.screen4

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface Screen4Dao {
    @Query("SELECT * FROM table_workspaces WHERE archivedAtMillis IS NULL ORDER BY id")
    suspend fun getActiveWorkspaces(): List<TableWorkspaceEntity>

    @Insert
    suspend fun insertWorkspace(workspace: TableWorkspaceEntity): Long

    @Query("SELECT * FROM table_workspaces WHERE archivedAtMillis IS NULL AND id = :workspaceId LIMIT 1")
    suspend fun getActiveWorkspaceById(workspaceId: Long): TableWorkspaceEntity?

    @Query("SELECT * FROM table_workspaces WHERE archivedAtMillis IS NOT NULL ORDER BY archivedAtMillis DESC")
    suspend fun getArchivedWorkspaces(): List<TableWorkspaceEntity>

    @Query("UPDATE table_workspaces SET archivedAtMillis = :archivedAtMillis WHERE id = :workspaceId")
    suspend fun archiveWorkspace(workspaceId: Long, archivedAtMillis: Long): Int

    @Query("UPDATE table_workspaces SET archivedAtMillis = NULL WHERE id = :workspaceId")
    suspend fun restoreWorkspace(workspaceId: Long): Int

    @Query("DELETE FROM table_workspaces WHERE id = :workspaceId")
    suspend fun deleteWorkspace(workspaceId: Long): Int

    @Query("UPDATE table_workspaces SET name = :name WHERE id = :workspaceId")
    suspend fun renameWorkspace(workspaceId: Long, name: String): Int

    @Query("SELECT * FROM table_workspaces WHERE id = :workspaceId LIMIT 1")
    suspend fun getWorkspaceById(workspaceId: Long): TableWorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTemplates(templates: List<ColumnTemplateEntity>): List<Long>

    @Query("SELECT * FROM column_templates ORDER BY id")
    suspend fun getTemplates(): List<ColumnTemplateEntity>

    @Insert
    suspend fun insertTemplate(template: ColumnTemplateEntity): Long

    @Query("SELECT * FROM column_templates WHERE fakerKey = :fakerKey LIMIT 1")
    suspend fun getTemplateByFakerKey(fakerKey: String): ColumnTemplateEntity?

    @Query("SELECT * FROM column_templates WHERE constraintType = :constraintType LIMIT 1")
    suspend fun getTemplateByConstraintType(constraintType: String): ColumnTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertColumns(columns: List<ColumnEntity>): List<Long>

    @Insert
    suspend fun insertColumn(column: ColumnEntity): Long

    @Update
    suspend fun updateColumn(column: ColumnEntity)

    @Query("SELECT * FROM columns WHERE workspaceId = :workspaceId AND isActive = 1 ORDER BY position")
    suspend fun getActiveColumns(workspaceId: Long): List<ColumnEntity>

    @Query("UPDATE columns SET isActive = 0 WHERE workspaceId = :workspaceId AND id IN (:columnIds)")
    suspend fun deactivateColumns(workspaceId: Long, columnIds: List<Long>)

    @Insert
    suspend fun insertRow(row: RowEntity): Long

    @Insert
    suspend fun insertCells(cells: List<CellEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCells(cells: List<CellEntity>)

    @Query("DELETE FROM rows WHERE workspaceId = :workspaceId AND id = :rowId")
    suspend fun deleteRow(workspaceId: Long, rowId: Long)

    @Query("SELECT * FROM rows WHERE workspaceId = :workspaceId ORDER BY id DESC LIMIT 1")
    suspend fun getLatestRow(workspaceId: Long): RowEntity?

    @Query("DELETE FROM rows WHERE workspaceId = :workspaceId")
    suspend fun deleteAllRows(workspaceId: Long)

    @Query("SELECT * FROM rows WHERE workspaceId = :workspaceId ORDER BY id DESC LIMIT :limit")
    suspend fun getRows(workspaceId: Long, limit: Int): List<RowEntity>

    @Query("SELECT * FROM rows WHERE workspaceId = :workspaceId AND id = :rowId LIMIT 1")
    suspend fun getRowById(workspaceId: Long, rowId: Long): RowEntity?

    @Query(
        """
        SELECT c.rowId, c.columnId, c.value
        FROM cells c
        WHERE c.workspaceId = :workspaceId AND c.rowId IN (:rowIds)
        """
    )
    suspend fun getCellsForRows(workspaceId: Long, rowIds: List<Long>): List<RowCellRecord>

    @Query(
        """
        SELECT c.rowId, c.columnId, c.value
        FROM cells c
        WHERE c.workspaceId = :workspaceId AND c.rowId = :rowId
        """
    )
    suspend fun getCellsForRow(workspaceId: Long, rowId: Long): List<RowCellRecord>

    @Query("SELECT COALESCE(MAX(position), -1) FROM columns WHERE workspaceId = :workspaceId")
    suspend fun getMaxColumnPosition(workspaceId: Long): Int

    @Query("SELECT * FROM columns WHERE workspaceId = :workspaceId AND id = :columnId LIMIT 1")
    suspend fun getColumnById(workspaceId: Long, columnId: Long): ColumnEntity?
}

data class RowCellRecord(
    val rowId: Long,
    val columnId: Long,
    val value: String,
)
