package com.example.templei.feature.soundboard

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

class ClipIndexRepository(private val context: Context) {

    data class IndexedClip(
        val clipId: String,
        val clipUri: String,
        val folderName: String,
        val fileName: String,
        val durationMs: Long,
        val playable: Boolean,
        val lastSeenTimestampMs: Long
    )

    data class RebuildSummary(
        val folderCount: Int,
        val clipCount: Int,
        val playableCount: Int
    )

    fun getPersistedRootUri(): Uri? {
        val raw = prefs().getString(KEY_ROOT_FOLDER_URI, null)
        return raw?.let(Uri::parse)
    }

    fun setPersistedRootUri(uri: Uri) {
        prefs().edit().putString(KEY_ROOT_FOLDER_URI, uri.toString()).apply()
    }

    fun getPersistedSelectedFolderName(): String? {
        return prefs().getString(KEY_SELECTED_FOLDER_NAME, null)?.takeIf { it.isNotBlank() }
    }

    fun setPersistedSelectedFolderName(folderName: String?) {
        prefs().edit()
            .putString(KEY_SELECTED_FOLDER_NAME, folderName)
            .apply()
    }

    fun rebuildIndex(rootUri: Uri): RebuildSummary {
        val root = DocumentFile.fromTreeUri(context, rootUri)
            ?.takeIf { it.canRead() }
            ?: return RebuildSummary(0, 0, 0)

        val now = System.currentTimeMillis()
        val rows = mutableListOf<IndexedClip>()

        val siblingDirectories = root.listFiles()
            .filter { it.isDirectory && it.canRead() }
            .sortedBy { it.name ?: "" }

        val folders = mutableListOf<Pair<String, DocumentFile>>()
        if (root.listFiles().any { it.isFile }) {
            folders += rootFolderName(root) to root
        }
        siblingDirectories.forEach { dir ->
            folders += ((dir.name ?: UNKNOWN_FOLDER_NAME) to dir)
        }

        folders.forEach { (folderName, folderDocument) ->
            folderDocument.listFiles()
                .filter { it.isFile && isSupportedExtension(it.name ?: "") }
                .forEach { file ->
                    val fileName = file.name ?: return@forEach
                    val durationMs = readDurationMs(file.uri) ?: 0L
                    val playable = durationMs in 1..MAX_SOUND_DURATION_MS
                    rows += IndexedClip(
                        clipId = file.uri.toString(),
                        clipUri = file.uri.toString(),
                        folderName = folderName,
                        fileName = fileName,
                        durationMs = durationMs,
                        playable = playable,
                        lastSeenTimestampMs = now
                    )
                }
        }

        persistRows(rows)

        return RebuildSummary(
            folderCount = rows.map { it.folderName }.toSet().size,
            clipCount = rows.size,
            playableCount = rows.count { it.playable }
        )
    }

    fun refreshFolder(rootUri: Uri, folderName: String): RebuildSummary {
        val root = DocumentFile.fromTreeUri(context, rootUri)
            ?.takeIf { it.canRead() }
            ?: return RebuildSummary(0, 0, 0)
        val folderDocument = resolveFolderDocument(root, folderName)
            ?: return replaceFolderRows(folderName, emptyList())
        val refreshedRows = buildFolderRows(folderName, folderDocument, System.currentTimeMillis())
        return replaceFolderRows(folderName, refreshedRows)
    }

    fun getIndexedFolders(): List<String> {
        return loadRows()
            .map { it.folderName }
            .distinct()
            .sorted()
    }

    fun getAllIndexedClips(): List<IndexedClip> = loadRows()

    fun getIndexedClipsForFolder(folderName: String): List<IndexedClip> {
        return loadRows()
            .asSequence()
            .filter { it.folderName == folderName }
            .sortedBy { it.fileName.lowercase() }
            .toList()
    }

    fun latestIndexedTimestampMs(): Long {
        return loadRows().maxOfOrNull { it.lastSeenTimestampMs } ?: 0L
    }

    private fun replaceFolderRows(folderName: String, refreshedRows: List<IndexedClip>): RebuildSummary {
        val mergedRows = loadRows()
            .filterNot { it.folderName == folderName } +
            refreshedRows
        persistRows(mergedRows)
        return RebuildSummary(
            folderCount = mergedRows.map { it.folderName }.toSet().size,
            clipCount = refreshedRows.size,
            playableCount = refreshedRows.count { it.playable }
        )
    }

    private fun resolveFolderDocument(root: DocumentFile, folderName: String): DocumentFile? {
        return when (folderName) {
            CURRENT_FOLDER_NAME,
            rootFolderName(root) -> root.takeIf { it.canRead() }
            else -> root.listFiles()
                .firstOrNull { it.isDirectory && it.canRead() && (it.name ?: UNKNOWN_FOLDER_NAME) == folderName }
        }
    }

    private fun buildFolderRows(
        folderName: String,
        folderDocument: DocumentFile,
        now: Long,
    ): List<IndexedClip> {
        return folderDocument.listFiles()
            .filter { it.isFile && isSupportedExtension(it.name ?: "") }
            .mapNotNull { file ->
                val fileName = file.name ?: return@mapNotNull null
                val durationMs = readDurationMs(file.uri) ?: 0L
                val playable = durationMs in 1..MAX_SOUND_DURATION_MS
                IndexedClip(
                    clipId = file.uri.toString(),
                    clipUri = file.uri.toString(),
                    folderName = folderName,
                    fileName = fileName,
                    durationMs = durationMs,
                    playable = playable,
                    lastSeenTimestampMs = now
                )
            }
    }

    private fun persistRows(rows: List<IndexedClip>) {
        val payload = JSONArray()
        rows.forEach { row ->
            payload.put(
                JSONObject()
                    .put("clipId", row.clipId)
                    .put("clipUri", row.clipUri)
                    .put("folderName", row.folderName)
                    .put("fileName", row.fileName)
                    .put("durationMs", row.durationMs)
                    .put("playable", row.playable)
                    .put("lastSeenTimestampMs", row.lastSeenTimestampMs)
            )
        }
        prefs().edit().putString(KEY_INDEX_ROWS, payload.toString()).apply()
    }

    private fun loadRows(): List<IndexedClip> {
        val raw = prefs().getString(KEY_INDEX_ROWS, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val clipId = item.optString("clipId", "")
                val clipUri = item.optString("clipUri", "")
                val folderName = item.optString("folderName", "")
                val fileName = item.optString("fileName", "")
                if (clipId.isBlank() || clipUri.isBlank() || folderName.isBlank() || fileName.isBlank()) continue
                add(
                    IndexedClip(
                        clipId = clipId,
                        clipUri = clipUri,
                        folderName = folderName,
                        fileName = fileName,
                        durationMs = item.optLong("durationMs", 0L),
                        playable = item.optBoolean("playable", false),
                        lastSeenTimestampMs = item.optLong("lastSeenTimestampMs", 0L)
                    )
                )
            }
        }
    }

    private fun readDurationMs(uri: Uri): Long? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()
            duration
        }.getOrNull()
    }

    private fun isSupportedExtension(displayName: String): Boolean {
        return displayName.endsWith(".wav", ignoreCase = true) || displayName.endsWith(".mp3", ignoreCase = true)
    }

    private fun rootFolderName(root: DocumentFile): String {
        return root.name?.takeIf { it.isNotBlank() } ?: CURRENT_FOLDER_NAME
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        private const val PREFS_NAME = "screen3_soundboard"
        private const val KEY_ROOT_FOLDER_URI = "root_folder_uri"
        private const val KEY_INDEX_ROWS = "clip_index_rows"
        private const val KEY_SELECTED_FOLDER_NAME = "selected_folder_name"
        private const val MAX_SOUND_DURATION_MS = 6_000L
        private const val CURRENT_FOLDER_NAME = "current-folder"
        private const val UNKNOWN_FOLDER_NAME = "unknown"
    }
}
