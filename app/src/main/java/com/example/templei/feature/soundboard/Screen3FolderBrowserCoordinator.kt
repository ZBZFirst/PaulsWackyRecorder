package com.example.templei.feature.soundboard

import android.net.Uri
import kotlin.math.max

/**
 * Coordinates Screen 3 folder/index browsing against [ClipIndexRepository].
 *
 * This helper centralizes root/index/folder list behavior so Activity code can
 * delegate folder navigation and clip-list binding concerns.
 */
class Screen3FolderBrowserCoordinator(
    private val clipIndexRepository: ClipIndexRepository
) {

    data class IndexedFolderState(
        val rootUri: Uri?,
        val folderNames: List<String>,
        val selectedFolderIndex: Int,
        val selectedFolderName: String?,
        val clips: List<IndexedClip>,
        val hasRootSelection: Boolean
    )

    data class IndexedClip(
        val clipId: String,
        val fileName: String,
        val clipUri: Uri,
        val folderName: String,
        val durationMs: Long,
        val playable: Boolean
    )

    data class RebuildSummary(
        val folderCount: Int,
        val clipCount: Int,
        val playableCount: Int
    )

    private var rootUri: Uri? = null
    private var folderNames: List<String> = emptyList()
    private var selectedFolderIndex: Int = 0

    /** Initializes from persisted root/index state if available. */
    fun initializeFromPersistedRoot(): IndexedFolderState {
        rootUri = clipIndexRepository.getPersistedRootUri()
        return if (rootUri == null) {
            buildState(emptyList())
        } else {
            hydrateFoldersFromIndex()
            buildState(currentFolderClips())
        }
    }

    /** Persists a newly picked root URI and rebuilds index for it. */
    fun setPickedRootAndRebuild(uri: Uri): RebuildSummary {
        rootUri = uri
        clipIndexRepository.setPersistedRootUri(uri)
        val summary = clipIndexRepository.rebuildIndex(uri)
        hydrateFoldersFromIndex()
        return RebuildSummary(
            folderCount = summary.folderCount,
            clipCount = summary.clipCount,
            playableCount = summary.playableCount
        )
    }

    /** Rebuilds index for current root if available; no-op summary otherwise. */
    fun rebuildCurrentRoot(): RebuildSummary {
        val current = rootUri ?: clipIndexRepository.getPersistedRootUri().also { rootUri = it }
        if (current == null) {
            return RebuildSummary(folderCount = 0, clipCount = 0, playableCount = 0)
        }
        val summary = clipIndexRepository.rebuildIndex(current)
        hydrateFoldersFromIndex()
        return RebuildSummary(
            folderCount = summary.folderCount,
            clipCount = summary.clipCount,
            playableCount = summary.playableCount
        )
    }

    fun movePreviousFolder(): IndexedFolderState {
        if (folderNames.isNotEmpty()) {
            selectedFolderIndex = (selectedFolderIndex - 1 + folderNames.size) % folderNames.size
        }
        return buildState(currentFolderClips())
    }

    fun moveNextFolder(): IndexedFolderState {
        if (folderNames.isNotEmpty()) {
            selectedFolderIndex = (selectedFolderIndex + 1) % folderNames.size
        }
        return buildState(currentFolderClips())
    }

    fun selectFolder(index: Int): IndexedFolderState {
        selectedFolderIndex = index.coerceIn(0, max(0, folderNames.size - 1))
        return buildState(currentFolderClips())
    }

    fun currentState(): IndexedFolderState = buildState(currentFolderClips())

    private fun hydrateFoldersFromIndex() {
        folderNames = clipIndexRepository.getIndexedFolders()
        selectedFolderIndex = selectedFolderIndex.coerceIn(0, max(0, folderNames.size - 1))
    }

    private fun currentFolderClips(): List<IndexedClip> {
        val folder = folderNames.getOrNull(selectedFolderIndex) ?: return emptyList()
        return clipIndexRepository.getIndexedClipsForFolder(folder).map { indexed ->
            IndexedClip(
                clipId = indexed.clipId,
                fileName = indexed.fileName,
                clipUri = Uri.parse(indexed.clipUri),
                folderName = indexed.folderName,
                durationMs = indexed.durationMs,
                playable = indexed.playable
            )
        }
    }

    private fun buildState(clips: List<IndexedClip>): IndexedFolderState {
        return IndexedFolderState(
            rootUri = rootUri,
            folderNames = folderNames,
            selectedFolderIndex = selectedFolderIndex,
            selectedFolderName = folderNames.getOrNull(selectedFolderIndex),
            clips = clips,
            hasRootSelection = rootUri != null
        )
    }
}
