package com.example.templei.feature.soundboard

/**
 * Screen 3 soundboard state machine.
 *
 * Intent: keep playback behavior explicit, bounded, and button-driven.
 * Invariant: sound playback is started only from an explicit button press event.
 */
class SoundboardStateMachine {
    enum class ClipLoadState {
        UNLOADED,
        LOADING,
        LOADED,
        FAILED
    }

    enum class PlaybackRejectionReason {
        CLIP_NOT_ASSIGNED,
        CLIP_NOT_PLAYABLE,
        CLIP_LOAD_FAILED,
        COOLDOWN_ACTIVE,
        MAX_STREAMS_REACHED,
        ENGINE_ERROR
    }

    data class ConstraintSnapshot(
        val maxStreams: Int,
        val cooldownMs: Long,
        val maxCacheSize: Int,
        val cachePolicy: String
    )

    data class RejectionCounters(
        val cooldownActive: Int,
        val maxStreamsReached: Int,
        val clipLoadFailed: Int,
        val clipNotAssigned: Int,
        val clipNotPlayable: Int,
        val engineError: Int
    )

    data class ClipLoadSnapshot(
        val unloaded: Int,
        val loading: Int,
        val loaded: Int,
        val failed: Int
    )

    data class LastRejection(
        val reason: String,
        val detail: String,
        val atEpochMs: Long
    )

    data class FavoriteSlotAssignment(
        val slotIndex: Int,
        val clipId: String?,
        val label: String
    )

    data class FolderCacheState(
        val activeFolder: String?,
        val cachedClipIds: Set<String>,
        val pinnedClipIds: Set<String>
    )

    sealed interface State {
        data class Loading(
            val stage: LoadingStage,
            val foldersDiscovered: Int,
            val filesScanned: Int,
            val playableFound: Int,
            val indexed: Int,
            val totalEstimated: Int?
        ) : State
        data class Ready(
            val folderName: String?,
            val playableCount: Int,
            val activeStreams: Int,
            val cachedCount: Int,
            val favorites: List<FavoriteSlotAssignment>,
            val cacheState: FolderCacheState,
            val constraintSnapshot: ConstraintSnapshot,
            val rejectionCounters: RejectionCounters,
            val clipLoadSnapshot: ClipLoadSnapshot,
            val lastRejection: LastRejection?
        ) : State

        data class Playing(
            val fileName: String,
            val activeStreams: Int,
            val cacheState: FolderCacheState,
            val constraintSnapshot: ConstraintSnapshot,
            val clipLoadSnapshot: ClipLoadSnapshot,
            val lastRejection: LastRejection?
        ) : State

        data class Error(
            val message: String,
            val rejectionCounters: RejectionCounters,
            val lastRejection: LastRejection?
        ) : State
    }

    enum class LoadingStage {
        Discovering,
        Validating
    }

    private var state: State = State.Loading(
        stage = LoadingStage.Discovering,
        foldersDiscovered = 0,
        filesScanned = 0,
        playableFound = 0,
        indexed = 0,
        totalEstimated = null
    )

    fun currentState(): State = state

    fun onLoading() {
        state = State.Loading(
            stage = LoadingStage.Discovering,
            foldersDiscovered = 0,
            filesScanned = 0,
            playableFound = 0,
            indexed = 0,
            totalEstimated = null
        )
    }

    fun onLoadingProgress(
        stage: LoadingStage,
        foldersDiscovered: Int,
        filesScanned: Int,
        playableFound: Int,
        indexed: Int,
        totalEstimated: Int?
    ) {
        state = State.Loading(
            stage = stage,
            foldersDiscovered = foldersDiscovered,
            filesScanned = filesScanned,
            playableFound = playableFound,
            indexed = indexed,
            totalEstimated = totalEstimated
        )
    }

    fun onReady(
        folderName: String?,
        playableCount: Int,
        activeStreams: Int,
        cachedCount: Int,
        favorites: List<FavoriteSlotAssignment>,
        cacheState: FolderCacheState,
        constraintSnapshot: ConstraintSnapshot,
        rejectionCounters: RejectionCounters,
        clipLoadSnapshot: ClipLoadSnapshot,
        lastRejection: LastRejection?
    ) {
        state = State.Ready(
            folderName = folderName,
            playableCount = playableCount,
            activeStreams = activeStreams,
            cachedCount = cachedCount,
            favorites = favorites,
            cacheState = cacheState,
            constraintSnapshot = constraintSnapshot,
            rejectionCounters = rejectionCounters,
            clipLoadSnapshot = clipLoadSnapshot,
            lastRejection = lastRejection
        )
    }

    fun onPlayAccepted(
        fileName: String,
        activeStreams: Int,
        cacheState: FolderCacheState,
        constraintSnapshot: ConstraintSnapshot,
        clipLoadSnapshot: ClipLoadSnapshot,
        lastRejection: LastRejection?
    ) {
        state = State.Playing(
            fileName = fileName,
            activeStreams = activeStreams,
            cacheState = cacheState,
            constraintSnapshot = constraintSnapshot,
            clipLoadSnapshot = clipLoadSnapshot,
            lastRejection = lastRejection
        )
    }

    fun onRejected(
        reason: PlaybackRejectionReason,
        detail: String,
        rejectionCounters: RejectionCounters,
        lastRejection: LastRejection?
    ) {
        state = State.Error(message = "$reason: $detail", rejectionCounters = rejectionCounters, lastRejection = lastRejection)
    }

    fun onError(message: String, rejectionCounters: RejectionCounters, lastRejection: LastRejection?) {
        state = State.Error(message = message, rejectionCounters = rejectionCounters, lastRejection = lastRejection)
    }
}
