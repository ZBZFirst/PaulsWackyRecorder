package com.example.templei.feature.screen4

import android.net.Uri
import com.example.templei.feature.soundboard.Screen3SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.roundToInt
import java.util.Locale

/**
 * Visual loop-sequencer coordinator for Screen 4.
 *
 * Screen 4 hosts the transport and multi-bar sequencing flow, while Screen 3
 * remains the source of truth for favorite-pad page assignments.
 */
class Screen4Coordinator(
    private val sampleLibraryRepository: Screen4SampleLibraryRepository,
    private val sharedFavoritesStore: Screen3SettingsStore,
    private val sequenceStore: Screen4SequenceStore,
    private val schedulerEngine: Screen4SchedulerEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playBars = MutableList(DEFAULT_PLAY_BAR_COUNT) {
        MutableList(STEP_COUNT) { null as Screen4FavoritePadReference? }
    }
    private val barEffectStates = MutableList(DEFAULT_PLAY_BAR_COUNT) { Screen4BarEffectState() }
    private val barSelectionStates = MutableList(DEFAULT_PLAY_BAR_COUNT) { BarSelectionState() }
    private val barNames = MutableList(DEFAULT_PLAY_BAR_COUNT) { barIndex -> defaultBarDisplayName(barIndex) }
    private var favoritePages: List<SharedFavoritePage> = listOf(emptyFavoritePage(DEFAULT_FAVORITE_PAGE_ID))
    private var displayedFavoritePageId: String = DEFAULT_FAVORITE_PAGE_ID

    private val mutableUiState = MutableStateFlow(
        Screen4UiState(
            currentFavoritePageId = DEFAULT_FAVORITE_PAGE_ID,
            favoriteSlots = buildFavoriteSlots(),
            sequenceBars = buildSequenceBars(),
        )
    )

    private var samplePackIndex: Screen4SamplePackIndex = emptySamplePackIndex()
    private var activeCompiledPattern: Screen4CompiledPattern? = null
    private var pendingCompiledPattern: Screen4CompiledPattern? = null

    val uiState: StateFlow<Screen4UiState> = mutableUiState.asStateFlow()

    fun initialize() {
        restoreWorkingState(sequenceStore.loadWorkingState())
        mutableUiState.value = mutableUiState.value.copy(
            bpm = mutableUiState.value.bpm.coerceIn(MIN_BPM, MAX_BPM),
            sequenceBars = buildSequenceBars(),
            runtimeStatus = "Loading sample library...",
            lastError = null,
        )
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { sampleLibraryRepository.loadIndex() }
            }.onSuccess { index ->
                samplePackIndex = index
                synchronizeSharedFavorites()
                applyUiRefresh(
                    runtimeStatus = initialRuntimeStatus(index),
                    lastError = null,
                )
            }.onFailure {
                mutableUiState.value = mutableUiState.value.copy(
                    transportState = Screen4TransportState.ERROR,
                    runtimeStatus = "Sample library failed to load.",
                    lastError = it.message ?: "unknown",
                )
            }
        }
    }

    fun refreshSharedPad() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { sampleLibraryRepository.loadIndex() }
            }.onSuccess { index ->
                samplePackIndex = index
                synchronizeSharedFavorites()
                applyUiRefresh(
                    runtimeStatus = if (index.rootUri == null) {
                        "Use Screen 3 to choose a sample folder and assign favorite pads."
                    } else {
                        "Favorites pad synced from Screen 3."
                    },
                    lastError = null,
                )
                if (mutableUiState.value.isPlaying) {
                    queueCurrentLoopForNextCycle()
                } else {
                    updateCompileStatusFromVisualLoop()
                }
            }.onFailure {
                mutableUiState.value = mutableUiState.value.copy(
                    transportState = Screen4TransportState.ERROR,
                    runtimeStatus = "Favorites pad sync failed.",
                    lastError = it.message ?: "unknown",
                )
            }
        }
    }

    fun previewFavoriteSlot(slotIndex: Int) {
        val normalizedSlotIndex = slotIndex.coerceIn(0, FAVORITE_SLOT_COUNT - 1)
        val clipId = currentFavoritePage().clipIds[normalizedSlotIndex]
        if (clipId == null) {
            mutableUiState.value = mutableUiState.value.copy(
                runtimeStatus = "Pad ${normalizedSlotIndex + 1} is empty. Assign it in Screen 3 first.",
                lastError = "Favorite pad ${normalizedSlotIndex + 1} is empty.",
            )
            return
        }
        val descriptor = resolveDescriptorByClipId(clipId)
        if (descriptor == null) {
            mutableUiState.value = mutableUiState.value.copy(
                runtimeStatus = "Pad ${normalizedSlotIndex + 1} clip is unavailable. Refresh Screen 3 first.",
                lastError = "Favorite pad ${normalizedSlotIndex + 1} clip is unavailable.",
            )
            return
        }
        scope.launch {
            runCatching { schedulerEngine.previewSample(descriptor) }
                .onSuccess {
                    mutableUiState.value = mutableUiState.value.copy(
                        runtimeStatus = "Previewed pad ${normalizedSlotIndex + 1}.",
                        lastError = null,
                    )
                }
                .onFailure {
                    mutableUiState.value = mutableUiState.value.copy(
                        transportState = Screen4TransportState.ERROR,
                        runtimeStatus = "Pad preview failed.",
                        lastError = it.message ?: "unknown",
                    )
                }
        }
    }

    fun showPreviousFavoritePage() {
        val currentIndex = currentFavoritePageIndex()
        if (currentIndex <= 0) return
        displayedFavoritePageId = favoritePages[currentIndex - 1].pageId
        sharedFavoritesStore.saveSelectedFavoritePageId(displayedFavoritePageId)
        persistWorkingState()
        applyUiRefresh(
            runtimeStatus = "Viewing favorite page ${currentFavoritePageIndex() + 1}.",
            lastError = null,
        )
    }

    fun showNextFavoritePage() {
        val currentIndex = currentFavoritePageIndex()
        if (currentIndex >= favoritePages.lastIndex) return
        displayedFavoritePageId = favoritePages[currentIndex + 1].pageId
        sharedFavoritesStore.saveSelectedFavoritePageId(displayedFavoritePageId)
        persistWorkingState()
        applyUiRefresh(
            runtimeStatus = "Viewing favorite page ${currentFavoritePageIndex() + 1}.",
            lastError = null,
        )
    }

    fun addFavoritePage() {
        val state = sharedFavoritesStore.loadFavoritePagesState(FAVORITE_SLOT_COUNT)
        val newPageId = "favorite_page_${System.currentTimeMillis()}_${state.pages.size + 1}"
        val updatedPages = state.pages + Screen3SettingsStore.FavoritePage(
            pageId = newPageId,
            assignments = emptyMap(),
        )
        sharedFavoritesStore.saveFavoritePagesState(
            state = Screen3SettingsStore.FavoritePagesState(
                pages = updatedPages,
                selectedPageId = newPageId,
            ),
            favoriteSlotCount = FAVORITE_SLOT_COUNT,
        )
        displayedFavoritePageId = newPageId
        synchronizeSharedFavorites()
        applyUiRefresh(
            runtimeStatus = "Created favorite page ${currentFavoritePageIndex() + 1}.",
            lastError = null,
        )
    }

    fun removeCurrentFavoritePage(): Boolean {
        val state = sharedFavoritesStore.loadFavoritePagesState(FAVORITE_SLOT_COUNT)
        if (state.pages.size <= 1) {
            mutableUiState.value = mutableUiState.value.copy(
                runtimeStatus = "At least one favorite page must remain.",
                lastError = null,
            )
            return false
        }
        val currentIndex = state.pages.indexOfFirst { it.pageId == displayedFavoritePageId }
            .takeIf { it >= 0 }
            ?: 0
        val updatedPages = state.pages.toMutableList().apply { removeAt(currentIndex) }
        val nextPageId = updatedPages[currentIndex.coerceAtMost(updatedPages.lastIndex)].pageId
        sharedFavoritesStore.saveFavoritePagesState(
            state = Screen3SettingsStore.FavoritePagesState(
                pages = updatedPages,
                selectedPageId = nextPageId,
            ),
            favoriteSlotCount = FAVORITE_SLOT_COUNT,
        )
        displayedFavoritePageId = nextPageId
        synchronizeSharedFavorites()
        applyUiRefresh(
            runtimeStatus = "Removed favorite page. Now viewing page ${currentFavoritePageIndex() + 1}.",
            lastError = null,
        )
        return true
    }

    fun addPlayBar() {
        if (playBars.size >= MAX_PLAY_BAR_COUNT) {
            mutableUiState.value = mutableUiState.value.copy(
                runtimeStatus = "Screen 4 supports up to $MAX_PLAY_BAR_COUNT play bars.",
                lastError = null,
            )
            return
        }
        playBars += MutableList(STEP_COUNT) { null as Screen4FavoritePadReference? }
        barEffectStates += Screen4BarEffectState()
        barSelectionStates += BarSelectionState()
        barNames += defaultBarDisplayName(playBars.lastIndex)
        persistWorkingState()
        applyUiRefresh(
            runtimeStatus = "Added play bar ${playBars.size}.",
            lastError = null,
        )
        if (mutableUiState.value.isPlaying) {
            queueCurrentLoopForNextCycle()
        } else {
            updateCompileStatusFromVisualLoop()
        }
    }

    fun removePlayBar() {
        removePlayBarAt(playBars.lastIndex)
    }

    fun removePlayBarAt(barIndex: Int) {
        if (playBars.size <= MIN_PLAY_BAR_COUNT) {
            mutableUiState.value = mutableUiState.value.copy(
                runtimeStatus = "At least one play bar must remain.",
                lastError = null,
            )
            return
        }
        val normalizedBarIndex = barIndex.coerceIn(0, playBars.lastIndex)
        playBars.removeAt(normalizedBarIndex)
        barEffectStates.removeAt(normalizedBarIndex)
        barSelectionStates.removeAt(normalizedBarIndex)
        barNames.removeAt(normalizedBarIndex)
        persistWorkingState()
        applyUiRefresh(
            runtimeStatus = "Removed bar ${normalizedBarIndex + 1}. ${playBars.size} bar(s) remain.",
            lastError = null,
        )
        if (mutableUiState.value.isPlaying) {
            queueCurrentLoopForNextCycle()
        } else {
            updateCompileStatusFromVisualLoop()
        }
    }

    fun updateBpm(value: Int) {
        val parsed = value.coerceIn(MIN_BPM, MAX_BPM)
        mutableUiState.value = mutableUiState.value.copy(bpm = parsed)
        persistWorkingState()
        if (mutableUiState.value.isPlaying) {
            scope.launch {
                runCatching { schedulerEngine.updateTempo(parsed) }
                    .onSuccess {
                        mutableUiState.value = mutableUiState.value.copy(
                            runtimeStatus = "Tempo updated to $parsed BPM.",
                            lastError = null,
                        )
                    }
                    .onFailure {
                        mutableUiState.value = mutableUiState.value.copy(
                            transportState = Screen4TransportState.ERROR,
                            runtimeStatus = "Tempo update failed.",
                            lastError = it.message ?: "unknown",
                        )
                    }
            }
        } else {
            updateCompileStatusFromVisualLoop()
        }
    }

    fun updateBarGain(barIndex: Int, gain: Float) {
        val normalized = gain.coerceIn(0f, 1f)
        updateBarEffectState(barIndex) {
            it.copy(
                gain = normalized,
                gainEnabled = normalized < 0.999f,
                gainLevel = effectLevelForSend(normalized),
            )
        }
    }

    fun updateBarPitchSemitones(barIndex: Int, pitchSemitones: Float) {
        val normalized = pitchSemitones.coerceIn(-12f, 12f)
        updateBarEffectState(barIndex) {
            it.copy(
                pitchSemitones = normalized,
                pitchEnabled = kotlin.math.abs(normalized) > 0.05f,
                pitchLevel = effectLevelForPitch(normalized),
            )
        }
    }

    fun updateBarPan(barIndex: Int, pan: Float) {
        val normalized = pan.coerceIn(-1f, 1f)
        updateBarEffectState(barIndex) {
            it.copy(
                pan = normalized,
                panEnabled = kotlin.math.abs(normalized) > 0.05f,
                panLevel = effectLevelForPan(normalized),
            )
        }
    }

    fun updateBarDelaySend(barIndex: Int, delaySend: Float) {
        val normalized = delaySend.coerceIn(0f, 1f)
        updateBarEffectState(barIndex) {
            it.copy(
                delaySend = normalized,
            )
        }
    }

    fun updateBarReverbSend(barIndex: Int, reverbSend: Float) {
        val normalized = reverbSend.coerceIn(0f, 1f)
        updateBarEffectState(barIndex) {
            it.copy(
                reverbSend = normalized,
                reverbEnabled = normalized > 0f,
                reverbLevel = effectLevelForSend(normalized),
            )
        }
    }

    fun ensurePlayBarCount(targetCount: Int) {
        val normalizedTarget = targetCount.coerceIn(MIN_PLAY_BAR_COUNT, MAX_PLAY_BAR_COUNT)
        var changed = false
        while (playBars.size < normalizedTarget) {
            playBars += MutableList(STEP_COUNT) { null as Screen4FavoritePadReference? }
            barEffectStates += Screen4BarEffectState()
            barSelectionStates += BarSelectionState()
            barNames += defaultBarDisplayName(playBars.lastIndex)
            changed = true
        }
        while (playBars.size > normalizedTarget) {
            playBars.removeAt(playBars.lastIndex)
            barEffectStates.removeAt(barEffectStates.lastIndex)
            barSelectionStates.removeAt(barSelectionStates.lastIndex)
            barNames.removeAt(barNames.lastIndex)
            changed = true
        }
        if (!changed) return
        persistWorkingState()
        applyUiRefresh(
            runtimeStatus = "Channel roster synced to $normalizedTarget channels.",
            lastError = null,
        )
    }

    fun prepareTutorialWorkspace() {
        scope.launch {
            schedulerEngine.stop()
            playBars.indices.forEach { barIndex ->
                playBars[barIndex] = MutableList(STEP_COUNT) { null }
            }
            barSelectionStates.indices.forEach { barIndex ->
                barSelectionStates[barIndex] = BarSelectionState()
            }
            persistWorkingState()
            mutableUiState.value = mutableUiState.value.copy(
                transportState = Screen4TransportState.STOPPED,
                isPlaying = false,
                activeCycle = 0L,
                activeStepIndex = 0,
                pendingPatternSummary = "No queued next-cycle update.",
            )
            applyUiRefresh(
                runtimeStatus = "Screen 4 tutorial workspace is ready.",
                lastError = null,
            )
            updateCompileStatusFromVisualLoop()
        }
    }

    fun toggleGainEnabled(barIndex: Int) {
        updateBarEffectState(barIndex) {
            val enabled = !it.gainEnabled
            it.copy(
                gainEnabled = enabled,
                gain = if (enabled) sendForEffectLevel(it.gainLevel) else 1f,
            )
        }
    }

    fun updateGainLevel(barIndex: Int, level: Int) {
        val normalizedLevel = level.coerceIn(1, 10)
        updateBarEffectState(barIndex) {
            it.copy(
                gainEnabled = true,
                gainLevel = normalizedLevel,
                gain = sendForEffectLevel(normalizedLevel),
            )
        }
    }

    fun togglePitchEnabled(barIndex: Int) {
        updateBarEffectState(barIndex) {
            val enabled = !it.pitchEnabled
            it.copy(
                pitchEnabled = enabled,
                pitchSemitones = if (enabled) pitchForEffectLevel(it.pitchLevel) else 0f,
            )
        }
    }

    fun updatePitchLevel(barIndex: Int, level: Int) {
        val normalizedLevel = level.coerceIn(1, 10)
        updateBarEffectState(barIndex) {
            it.copy(
                pitchEnabled = true,
                pitchLevel = normalizedLevel,
                pitchSemitones = pitchForEffectLevel(normalizedLevel),
            )
        }
    }

    fun toggleReverbEnabled(barIndex: Int) {
        updateBarEffectState(barIndex) {
            val enabled = !it.reverbEnabled
            it.copy(
                reverbEnabled = enabled,
                reverbSend = if (enabled) sendForEffectLevel(it.reverbLevel) else 0f,
            )
        }
    }

    fun updateReverbLevel(barIndex: Int, level: Int) {
        val normalizedLevel = level.coerceIn(1, 10)
        updateBarEffectState(barIndex) {
            it.copy(
                reverbEnabled = true,
                reverbLevel = normalizedLevel,
                reverbSend = sendForEffectLevel(normalizedLevel),
            )
        }
    }

    fun togglePanEnabled(barIndex: Int) {
        updateBarEffectState(barIndex) {
            val enabled = !it.panEnabled
            it.copy(
                panEnabled = enabled,
                pan = if (enabled) panForEffectLevel(it.panLevel) else 0f,
            )
        }
    }

    fun updatePanLevel(barIndex: Int, level: Int) {
        val normalizedLevel = level.coerceIn(1, 10)
        updateBarEffectState(barIndex) {
            it.copy(
                panEnabled = true,
                panLevel = normalizedLevel,
                pan = panForEffectLevel(normalizedLevel),
            )
        }
    }

    fun assignStepFromFavorite(
        barIndex: Int,
        stepIndex: Int,
        favoritePageId: String,
        favoriteSlotIndex: Int,
    ) {
        val normalizedBarIndex = barIndex.coerceIn(0, playBars.lastIndex)
        val normalizedStepIndex = stepIndex.coerceIn(0, STEP_COUNT - 1)
        val normalizedFavoriteSlotIndex = favoriteSlotIndex.coerceIn(0, FAVORITE_SLOT_COUNT - 1)
        val favoritePage = favoritePages.firstOrNull { it.pageId == favoritePageId }
        val clipId = favoritePage?.clipIds?.getOrNull(normalizedFavoriteSlotIndex)
        if (clipId == null) {
            mutableUiState.value = mutableUiState.value.copy(
                runtimeStatus = "Pad ${normalizedFavoriteSlotIndex + 1} is empty. Assign it in Screen 3 first.",
                lastError = "Favorite pad ${normalizedFavoriteSlotIndex + 1} is empty.",
            )
            return
        }
        playBars[normalizedBarIndex][normalizedStepIndex] = Screen4FavoritePadReference(
            pageId = favoritePageId,
            slotIndex = normalizedFavoriteSlotIndex,
            clipId = clipId,
        )
        persistWorkingState()
        applyUiRefresh(
            runtimeStatus = "Bar ${normalizedBarIndex + 1} step ${normalizedStepIndex + 1} assigned from page ${displayPageNumber(favoritePageId)} pad ${normalizedFavoriteSlotIndex + 1}.",
            lastError = null,
        )
        if (mutableUiState.value.isPlaying) {
            queueCurrentLoopForNextCycle()
        } else {
            updateCompileStatusFromVisualLoop()
        }
    }

    fun assignFavoriteToBar(
        barIndex: Int,
        favoritePageId: String,
        favoriteSlotIndex: Int,
        target: Screen4BatchAssignTarget,
        clearSelectionAfterAssign: Boolean = false,
    ) {
        val normalizedBarIndex = barIndex.coerceIn(0, playBars.lastIndex)
        val normalizedFavoriteSlotIndex = favoriteSlotIndex.coerceIn(0, FAVORITE_SLOT_COUNT - 1)
        val favoritePage = favoritePages.firstOrNull { it.pageId == favoritePageId }
        val clipId = favoritePage?.clipIds?.getOrNull(normalizedFavoriteSlotIndex)
        if (clipId == null) {
            mutableUiState.value = mutableUiState.value.copy(
                runtimeStatus = "Pad ${normalizedFavoriteSlotIndex + 1} is empty. Assign it in Screen 3 first.",
                lastError = "Favorite pad ${normalizedFavoriteSlotIndex + 1} is empty.",
            )
            return
        }

        val targetStepIndices = stepIndicesForBatchTarget(normalizedBarIndex, target)
        if (targetStepIndices.isEmpty()) {
            mutableUiState.value = mutableUiState.value.copy(
                runtimeStatus = "Select one or more steps before assigning a sound.",
                lastError = "No steps selected.",
            )
            return
        }

        targetStepIndices.forEach { stepIndex ->
            playBars[normalizedBarIndex][stepIndex] = Screen4FavoritePadReference(
                pageId = favoritePageId,
                slotIndex = normalizedFavoriteSlotIndex,
                clipId = clipId,
            )
        }
        if (target == Screen4BatchAssignTarget.SELECTED_SLOTS || clearSelectionAfterAssign) {
            barSelectionStates[normalizedBarIndex] = BarSelectionState()
        }
        persistWorkingState()
        applyUiRefresh(
            runtimeStatus = batchAssignmentStatus(
                barIndex = normalizedBarIndex,
                stepCount = targetStepIndices.size,
                favoritePageId = favoritePageId,
                favoriteSlotIndex = normalizedFavoriteSlotIndex,
                target = target,
            ),
            lastError = null,
        )
        if (mutableUiState.value.isPlaying) {
            queueCurrentLoopForNextCycle()
        } else {
            updateCompileStatusFromVisualLoop()
        }
    }

    fun clearStep(
        barIndex: Int,
        stepIndex: Int,
    ) {
        val normalizedBarIndex = barIndex.coerceIn(0, playBars.lastIndex)
        val normalizedStepIndex = stepIndex.coerceIn(0, STEP_COUNT - 1)
        playBars[normalizedBarIndex][normalizedStepIndex] = null
        persistWorkingState()
        applyUiRefresh(
            runtimeStatus = "Bar ${normalizedBarIndex + 1} step ${normalizedStepIndex + 1} cleared.",
            lastError = null,
        )
        if (mutableUiState.value.isPlaying) {
            queueCurrentLoopForNextCycle()
        } else {
            updateCompileStatusFromVisualLoop()
        }
    }

    fun toggleBarSelectionMode(barIndex: Int) {
        val normalizedBarIndex = barIndex.coerceIn(0, playBars.lastIndex)
        val currentState = barSelectionStates[normalizedBarIndex]
        barSelectionStates[normalizedBarIndex] = if (currentState.isSelectionModeEnabled) {
            BarSelectionState()
        } else {
            currentState.copy(isSelectionModeEnabled = true)
        }
        applyUiRefresh(
            runtimeStatus = if (barSelectionStates[normalizedBarIndex].isSelectionModeEnabled) {
                "Bar ${normalizedBarIndex + 1} selection mode is on."
            } else {
                "Bar ${normalizedBarIndex + 1} selection mode is off."
            },
            lastError = null,
        )
    }

    fun setBarSelectionMode(
        barIndex: Int,
        enabled: Boolean,
    ) {
        val normalizedBarIndex = barIndex.coerceIn(0, playBars.lastIndex)
        val currentState = barSelectionStates[normalizedBarIndex]
        val updatedState = if (enabled) {
            currentState.copy(isSelectionModeEnabled = true)
        } else {
            BarSelectionState()
        }
        if (updatedState == currentState) return
        barSelectionStates[normalizedBarIndex] = updatedState
        applyUiRefresh(
            runtimeStatus = if (enabled) {
                "Bar ${normalizedBarIndex + 1} selection mode is on."
            } else {
                "Bar ${normalizedBarIndex + 1} selection mode is off."
            },
            lastError = null,
        )
    }

    fun selectBatchTarget(
        barIndex: Int,
        target: Screen4BatchAssignTarget,
    ) {
        val normalizedBarIndex = barIndex.coerceIn(0, playBars.lastIndex)
        val selectedIndices = stepIndicesForBatchTarget(normalizedBarIndex, target)
        barSelectionStates[normalizedBarIndex] = barSelectionStates[normalizedBarIndex].copy(
            isSelectionModeEnabled = true,
            selectedStepIndices = selectedIndices,
        )
        applyUiRefresh(
            runtimeStatus = "Bar ${normalizedBarIndex + 1} selected ${selectedIndices.size} step(s).",
            lastError = null,
        )
    }

    fun clearSelectedAssignments(barIndex: Int) {
        val normalizedBarIndex = barIndex.coerceIn(0, playBars.lastIndex)
        val selectedIndices = barSelectionStates[normalizedBarIndex].selectedStepIndices
        if (selectedIndices.isEmpty()) {
            mutableUiState.value = mutableUiState.value.copy(
                runtimeStatus = "Select one or more steps before clearing them.",
                lastError = null,
            )
            return
        }
        selectedIndices.forEach { stepIndex ->
            playBars[normalizedBarIndex][stepIndex] = null
        }
        persistWorkingState()
        applyUiRefresh(
            runtimeStatus = "Cleared ${selectedIndices.size} selected step(s) in bar ${normalizedBarIndex + 1}.",
            lastError = null,
        )
        if (mutableUiState.value.isPlaying) {
            queueCurrentLoopForNextCycle()
        } else {
            updateCompileStatusFromVisualLoop()
        }
    }

    fun toggleSelectedStep(
        barIndex: Int,
        stepIndex: Int,
    ) {
        val normalizedBarIndex = barIndex.coerceIn(0, playBars.lastIndex)
        val normalizedStepIndex = stepIndex.coerceIn(0, STEP_COUNT - 1)
        val currentState = barSelectionStates[normalizedBarIndex]
        if (!currentState.isSelectionModeEnabled) return

        val updatedSelection = currentState.selectedStepIndices.toMutableSet().apply {
            if (!add(normalizedStepIndex)) {
                remove(normalizedStepIndex)
            }
        }
        barSelectionStates[normalizedBarIndex] = currentState.copy(
            selectedStepIndices = updatedSelection.toList().sorted(),
        )
        applyUiRefresh(
            runtimeStatus = "Bar ${normalizedBarIndex + 1} has ${updatedSelection.size} selected step(s).",
            lastError = null,
        )
    }

    fun savedBarSnapshots(): List<Screen4SavedBarSnapshot> {
        return sequenceStore.loadSavedBars()
    }

    fun savePlayBar(
        barIndex: Int,
        requestedName: String,
    ) {
        val normalizedBarIndex = barIndex.coerceIn(0, playBars.lastIndex)
        val normalizedName = requestedName.trim().ifBlank { nextDefaultBarName(normalizedBarIndex) }
        barNames[normalizedBarIndex] = normalizedName
        val replacedExisting = sequenceStore.saveBar(
            Screen4SavedBarSnapshot(
                name = normalizedName,
                savedAtMs = System.currentTimeMillis(),
                steps = snapshotPlayBar(normalizedBarIndex),
                effectState = barEffectStates[normalizedBarIndex],
            )
        )
        persistWorkingState()
        applyUiRefresh(
            runtimeStatus = if (replacedExisting) {
                "Replaced saved bar \"$normalizedName\"."
            } else {
                "Saved bar ${normalizedBarIndex + 1} as \"$normalizedName\"."
            },
            lastError = null,
        )
    }

    fun loadSavedPlayBar(
        barIndex: Int,
        savedName: String,
    ) {
        val normalizedBarIndex = barIndex.coerceIn(0, playBars.lastIndex)
        scope.launch {
            if (!refreshSavedLoadDependencies()) {
                return@launch
            }
            val snapshot = sequenceStore.loadSavedBars()
                .firstOrNull { it.name.equals(savedName, ignoreCase = true) }
            if (snapshot == null) {
                applyUiRefresh(
                    runtimeStatus = "Saved bar \"$savedName\" was not found.",
                    lastError = "Saved bar \"$savedName\" was not found.",
                )
                return@launch
            }
            val missingDependency = collectMissingDependencies(
                bars = listOf(snapshot.steps),
                barIndexOffset = normalizedBarIndex,
            ).firstOrNull()
            if (missingDependency != null) {
                applyUiRefresh(
                    runtimeStatus = "Cannot load \"$savedName\" because saved dependencies were deleted.",
                    lastError = formatMissingDependencyDetail(missingDependency),
                )
                return@launch
            }

            playBars[normalizedBarIndex] = normalizeStepSnapshot(snapshot.steps).toMutableList()
            barEffectStates[normalizedBarIndex] = snapshot.effectState
            barNames[normalizedBarIndex] = snapshot.name
            persistWorkingState()
            applyUiRefresh(
                runtimeStatus = "Loaded \"$savedName\" into bar ${normalizedBarIndex + 1}.",
                lastError = null,
            )
            if (mutableUiState.value.isPlaying) {
                queueCurrentLoopForNextCycle()
            } else {
                updateCompileStatusFromVisualLoop()
            }
        }
    }

    fun deleteSavedPlayBar(savedName: String) {
        val deleted = sequenceStore.deleteBar(savedName)
        applyUiRefresh(
            runtimeStatus = if (deleted) {
                "Deleted saved bar \"$savedName\"."
            } else {
                "Saved bar \"$savedName\" was not found."
            },
            lastError = if (deleted) null else "Saved bar \"$savedName\" was not found.",
        )
    }

    fun savedSongSnapshots(): List<Screen4SavedSongSnapshot> {
        return sequenceStore.loadSavedSongs()
    }

    fun saveSong(requestedName: String) {
        val normalizedName = requestedName.trim().ifBlank { nextDefaultSongName() }
        val replacedExisting = sequenceStore.saveSong(
            Screen4SavedSongSnapshot(
                name = normalizedName,
                savedAtMs = System.currentTimeMillis(),
                bpm = mutableUiState.value.bpm,
                displayedFavoritePageId = displayedFavoritePageId,
                playBars = snapshotPlayBars(),
                barEffects = snapshotBarEffects(),
                barNames = snapshotBarNames(),
            )
        )
        mutableUiState.value = mutableUiState.value.copy(
            runtimeStatus = if (replacedExisting) {
                "Replaced saved song \"$normalizedName\"."
            } else {
                "Saved song \"$normalizedName\"."
            },
            lastError = null,
        )
    }

    fun loadSavedSong(savedName: String) {
        scope.launch {
            if (!refreshSavedLoadDependencies()) {
                return@launch
            }
            val snapshot = sequenceStore.loadSavedSongs()
                .firstOrNull { it.name.equals(savedName, ignoreCase = true) }
            if (snapshot == null) {
                applyUiRefresh(
                    runtimeStatus = "Saved song \"$savedName\" was not found.",
                    lastError = "Saved song \"$savedName\" was not found.",
                )
                return@launch
            }
            val missingDependency = collectMissingDependencies(snapshot.playBars).firstOrNull()
            if (missingDependency != null) {
                applyUiRefresh(
                    runtimeStatus = "Cannot load \"$savedName\" because saved dependencies were deleted.",
                    lastError = formatMissingDependencyDetail(missingDependency),
                )
                return@launch
            }

            mutableUiState.value = mutableUiState.value.copy(
                bpm = snapshot.bpm.coerceIn(MIN_BPM, MAX_BPM),
            )
            displayedFavoritePageId = snapshot.displayedFavoritePageId ?: displayedFavoritePageId
            sharedFavoritesStore.saveSelectedFavoritePageId(displayedFavoritePageId)
            replacePlayBars(snapshot.playBars)
            replaceBarEffects(snapshot.barEffects)
            replaceBarNames(snapshot.barNames)
            persistWorkingState()
            applyUiRefresh(
                runtimeStatus = "Loaded song \"$savedName\".",
                lastError = null,
            )
            if (mutableUiState.value.isPlaying) {
                queueCurrentLoopForNextCycle()
            } else {
                updateCompileStatusFromVisualLoop()
            }
        }
    }

    fun deleteSavedSong(savedName: String) {
        val deleted = sequenceStore.deleteSong(savedName)
        applyUiRefresh(
            runtimeStatus = if (deleted) {
                "Deleted saved song \"$savedName\"."
            } else {
                "Saved song \"$savedName\" was not found."
            },
            lastError = if (deleted) null else "Saved song \"$savedName\" was not found.",
        )
    }

    fun play() {
        scope.launch {
            val compiled = buildVisualPatternForPlayback().getOrElse {
                mutableUiState.value = mutableUiState.value.copy(
                    transportState = Screen4TransportState.ERROR,
                    runtimeStatus = runtimeStatusForCompileFailure(it),
                    lastError = it.message ?: "unknown",
                    patternSource = mutableUiState.value.patternSource.copy(
                        compileResult = Screen4CompileResult.Failure(it.message ?: "unknown"),
                    ),
                )
                return@launch
            }

            mutableUiState.value = mutableUiState.value.copy(
                transportState = Screen4TransportState.STARTING,
                runtimeStatus = "Starting multi-bar loop...",
                lastError = null,
                patternSource = mutableUiState.value.patternSource.copy(
                    compileResult = Screen4CompileResult.Success(compiled),
                ),
            )

            runCatching {
                activeCompiledPattern = compiled
                pendingCompiledPattern = null
                schedulerEngine.start(compiled, mutableUiState.value.bpm)
            }.onSuccess {
                mutableUiState.value = mutableUiState.value.copy(
                    transportState = Screen4TransportState.RUNNING,
                    isPlaying = true,
                    activePatternSummary = compiled.summaryLabel(),
                    pendingPatternSummary = "No queued next-cycle update.",
                    runtimeStatus = "Loop transport running.",
                    lastError = null,
                )
            }.onFailure {
                mutableUiState.value = mutableUiState.value.copy(
                    transportState = Screen4TransportState.ERROR,
                    isPlaying = false,
                    runtimeStatus = "Loop transport failed to start.",
                    lastError = it.message ?: "unknown",
                    patternSource = mutableUiState.value.patternSource.copy(
                        compileResult = Screen4CompileResult.Failure(it.message ?: "unknown"),
                    ),
                )
            }
        }
    }

    fun stop() {
        scope.launch {
            schedulerEngine.stop()
            pendingCompiledPattern = null
            mutableUiState.value = mutableUiState.value.copy(
                transportState = Screen4TransportState.STOPPED,
                isPlaying = false,
                activeCycle = 0L,
                activeStepIndex = 0,
                pendingPatternSummary = "No queued next-cycle update.",
                runtimeStatus = "Loop transport stopped.",
            )
        }
    }

    fun release() {
        schedulerEngine.release()
    }

    fun onCycleWindow(cycleWindow: Screen4CycleWindow) {
        val activeSummary = pendingCompiledPattern?.let {
            activeCompiledPattern = it
            pendingCompiledPattern = null
            it.summaryLabel()
        } ?: activeCompiledPattern?.summaryLabel().orEmpty()

        mutableUiState.value = mutableUiState.value.copy(
            transportState = Screen4TransportState.RUNNING,
            isPlaying = true,
            activeCycle = cycleWindow.cycleIndex,
            activeStepIndex = 0,
            activePatternSummary = if (activeSummary.isBlank()) "No active loop." else activeSummary,
            pendingPatternSummary = pendingCompiledPattern?.let { "Queued next cycle: ${it.summaryLabel()}" }
                ?: "No queued next-cycle update.",
            runtimeStatus = "Loop cycle ${cycleWindow.cycleIndex + 1} running at ${cycleWindow.bpm} BPM.",
        )
    }

    fun onStepTick(stepIndex: Int) {
        mutableUiState.value = mutableUiState.value.copy(
            activeStepIndex = stepIndex,
        )
    }

    fun onRuntimeError(message: String) {
        mutableUiState.value = mutableUiState.value.copy(
            transportState = Screen4TransportState.ERROR,
            runtimeStatus = "Runtime error.",
            lastError = message,
        )
    }

    private fun queueCurrentLoopForNextCycle() {
        scope.launch {
            val compiled = buildVisualPatternForPlayback().getOrElse {
                mutableUiState.value = mutableUiState.value.copy(
                    patternSource = mutableUiState.value.patternSource.copy(
                        compileResult = Screen4CompileResult.Failure(it.message ?: "unknown"),
                    ),
                    runtimeStatus = "Current loop is invalid. Last good loop remains active.",
                    lastError = it.message ?: "unknown",
                )
                return@launch
            }
            runCatching { schedulerEngine.queuePattern(compiled, mutableUiState.value.bpm) }
                .onSuccess {
                    pendingCompiledPattern = compiled
                    mutableUiState.value = mutableUiState.value.copy(
                        patternSource = mutableUiState.value.patternSource.copy(
                            compileResult = Screen4CompileResult.Success(compiled),
                        ),
                        pendingPatternSummary = "Queued next cycle: ${compiled.summaryLabel()}",
                        runtimeStatus = "Loop update queued for the next cycle.",
                        lastError = null,
                    )
                }
                .onFailure {
                    mutableUiState.value = mutableUiState.value.copy(
                        patternSource = mutableUiState.value.patternSource.copy(
                            compileResult = Screen4CompileResult.Failure(it.message ?: "unknown"),
                        ),
                        runtimeStatus = "Loop update failed to preload.",
                        lastError = it.message ?: "unknown",
                    )
                }
        }
    }

    private fun updateCompileStatusFromVisualLoop() {
        val result = buildVisualPattern()
        mutableUiState.value = mutableUiState.value.copy(
            patternSource = mutableUiState.value.patternSource.copy(
                compileResult = result.fold(
                    onSuccess = { Screen4CompileResult.Success(it) },
                    onFailure = { Screen4CompileResult.Failure(it.message ?: "unknown") },
                ),
            ),
            activePatternSummary = result.getOrNull()?.summaryLabel() ?: "No active loop.",
        )
    }

    private fun buildVisualPattern(): Result<Screen4CompiledPattern> {
        return runCatching {
            if (samplePackIndex.rootUri == null) {
                throw IllegalArgumentException("Use Screen 3 to choose a sample folder before building a loop.")
            }

            val populatedSteps = playBars.flatMapIndexed { barIndex, bar ->
                bar.mapIndexedNotNull { stepIndex, favoriteReference ->
                    favoriteReference?.let { barIndex to (stepIndex to it) }
                }
            }
            if (populatedSteps.isEmpty()) {
                throw IllegalArgumentException("Assign at least one Screen 3 pad to any play bar.")
            }

            val resolvedSamples = mutableMapOf<String, Screen4SampleDescriptor>()
            val scheduledEvents = populatedSteps.flatMap { (barIndex, indexedStep) ->
                val stepIndex = indexedStep.first
                val favoriteReference = indexedStep.second
                val effectState = barEffectStates.getOrElse(barIndex) { Screen4BarEffectState() }
                val clipId = favoriteReference.clipId
                    ?: favoritePages.firstOrNull { it.pageId == favoriteReference.pageId }
                        ?.clipIds?.getOrNull(favoriteReference.slotIndex)
                    ?: throw IllegalArgumentException(
                        "Page ${displayPageNumber(favoriteReference.pageId)} pad ${favoriteReference.slotIndex + 1} is empty."
                    )
                val descriptor = resolveDescriptorByClipId(clipId)
                    ?: throw IllegalArgumentException(
                        "Saved dependencies were deleted. ${formatMissingDependencyDetail(
                            SavedDependencyIssue(
                                barIndex = barIndex,
                                stepIndex = stepIndex,
                                pageId = favoriteReference.pageId,
                                slotIndex = favoriteReference.slotIndex,
                            )
                        )}"
                    )
                resolvedSamples[descriptor.sampleId.lowercase(Locale.US)] = descriptor
                buildScheduledEventsForBarStep(
                    descriptor = descriptor,
                    stepIndex = stepIndex,
                    effectState = effectState,
                )
            }

            Screen4CompiledPattern(
                sourceText = "visual-loop",
                stepsPerCycle = STEP_COUNT,
                scheduledEvents = scheduledEvents,
                resolvedSamples = resolvedSamples,
            )
        }
    }

    private suspend fun buildVisualPatternForPlayback(): Result<Screen4CompiledPattern> {
        val initialAttempt = buildVisualPattern()
        if (initialAttempt.isSuccess) {
            return initialAttempt
        }

        val message = initialAttempt.exceptionOrNull()?.message.orEmpty()
        val shouldRetryAfterRefresh =
            message.contains("sample folder", ignoreCase = true) ||
                message.contains("Saved dependencies were deleted", ignoreCase = true)
        if (!shouldRetryAfterRefresh) {
            return initialAttempt
        }
        if (!refreshSavedLoadDependencies()) {
            return initialAttempt
        }
        return buildVisualPattern()
    }

    private fun runtimeStatusForCompileFailure(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("Assign at least one Screen 3 pad", ignoreCase = true) ->
                "Play blocked. Assign Screen 3 pads to at least one step first."
            message.contains("sample folder", ignoreCase = true) ->
                "Play blocked. Choose or refresh the Screen 3 sample folder first."
            message.contains("Saved dependencies were deleted", ignoreCase = true) ->
                "Play blocked. Some WAV dependencies were deleted."
            else -> "Play blocked."
        }
    }

    private fun buildScheduledEventsForBarStep(
        descriptor: Screen4SampleDescriptor,
        stepIndex: Int,
        effectState: Screen4BarEffectState,
    ): List<Screen4ScheduledEvent> {
        val baseStepPosition = stepIndex.toDouble()
        val speed = pitchSemitonesToSpeed(effectState.pitchSemitones)
        val events = mutableListOf(
            Screen4ScheduledEvent(
                sampleId = descriptor.sampleId,
                stepIndex = stepIndex,
                stepPosition = baseStepPosition,
                gain = effectState.gain,
                pan = effectState.pan,
                speed = speed,
            )
        )

        val delayGain = effectState.gain * effectState.delaySend * 0.6f
        if (delayGain > 0.02f) {
            events += Screen4ScheduledEvent(
                sampleId = descriptor.sampleId,
                stepIndex = stepIndex,
                stepPosition = baseStepPosition + 2.0,
                gain = delayGain.coerceIn(0f, 1f),
                pan = effectState.pan,
                speed = speed,
            )
        }

        val reverbFirstGain = effectState.gain * effectState.reverbSend * 0.25f
        val reverbSecondGain = effectState.gain * effectState.reverbSend * 0.12f
        if (reverbFirstGain > 0.02f) {
            events += Screen4ScheduledEvent(
                sampleId = descriptor.sampleId,
                stepIndex = stepIndex,
                stepPosition = baseStepPosition + 0.5,
                gain = reverbFirstGain.coerceIn(0f, 1f),
                pan = effectState.pan * 0.7f,
                speed = speed,
            )
        }
        if (reverbSecondGain > 0.02f) {
            events += Screen4ScheduledEvent(
                sampleId = descriptor.sampleId,
                stepIndex = stepIndex,
                stepPosition = baseStepPosition + 1.0,
                gain = reverbSecondGain.coerceIn(0f, 1f),
                pan = effectState.pan * 0.5f,
                speed = speed,
            )
        }
        return events
    }

    private fun pitchSemitonesToSpeed(pitchSemitones: Float): Float {
        return 2.0.pow((pitchSemitones / 12f).toDouble()).toFloat().coerceIn(0.5f, 2f)
    }

    private suspend fun refreshSavedLoadDependencies(): Boolean {
        return runCatching {
            withContext(Dispatchers.IO) { sampleLibraryRepository.refreshIndex() }
        }.onSuccess { index ->
            samplePackIndex = index
            synchronizeSharedFavorites()
        }.onFailure {
            mutableUiState.value = mutableUiState.value.copy(
                transportState = Screen4TransportState.ERROR,
                runtimeStatus = "Saved dependency check failed.",
                lastError = it.message ?: "unknown",
            )
        }.isSuccess
    }

    private fun collectMissingDependencies(
        bars: List<List<Screen4FavoritePadReference?>>,
        barIndexOffset: Int = 0,
    ): List<SavedDependencyIssue> {
        return bars.flatMapIndexed { offset, steps ->
            steps.mapIndexedNotNull { stepIndex, favoriteReference ->
                favoriteReference?.takeIf { currentClipIdForReference(it) == null || resolveDescriptorByClipId(currentClipIdForReference(it).orEmpty()) == null }
                    ?.let {
                        SavedDependencyIssue(
                            barIndex = barIndexOffset + offset,
                            stepIndex = stepIndex,
                            pageId = it.pageId,
                            slotIndex = it.slotIndex,
                        )
                    }
            }
        }
    }

    private fun currentClipIdForReference(reference: Screen4FavoritePadReference): String? {
        return reference.clipId ?: favoritePages.firstOrNull { it.pageId == reference.pageId }
            ?.clipIds?.getOrNull(reference.slotIndex)
    }

    private fun formatMissingDependencyDetail(issue: SavedDependencyIssue): String {
        return "Ch ${issue.barIndex + 1} Step ${issue.stepIndex + 1} depends on page ${displayPageNumber(issue.pageId)} pad ${issue.slotIndex + 1}, but that WAV was deleted."
    }

    private fun resolveDescriptorByClipId(clipId: String): Screen4SampleDescriptor? {
        return samplePackIndex.descriptorsByClipId[clipId]
    }

    private fun applyUiRefresh(
        runtimeStatus: String,
        lastError: String?,
    ) {
        mutableUiState.value = mutableUiState.value.copy(
            currentFavoritePageId = currentFavoritePage().pageId,
            currentFavoritePageIndex = currentFavoritePageIndex(),
            favoritePageCount = favoritePages.size,
            selectedRootLabel = samplePackIndex.rootUri?.toString() ?: "No sample folder selected.",
            sampleLibrarySummary = samplePackIndex.summaryLabel(),
            favoriteSlots = buildFavoriteSlots(),
            sequenceBars = buildSequenceBars(),
            runtimeStatus = runtimeStatus,
            lastError = lastError,
        )
    }

    private fun buildFavoriteSlots(): List<Screen4VisualSlot> {
        return currentFavoritePage().clipIds.mapIndexed { index, sampleId ->
            val slotLabel = String.format(Locale.US, "PAD %02d", index + 1)
            Screen4VisualSlot(
                index = index,
                sampleId = sampleId,
                label = sampleId?.let { "$slotLabel\n${displayLabelForClipId(it)}" }
                    ?: slotLabel,
            )
        }
    }

    private fun buildSequenceBars(): List<Screen4SequenceBarUi> {
        return playBars.mapIndexed { barIndex, steps ->
            val selectionState = barSelectionStates.getOrElse(barIndex) { BarSelectionState() }
            Screen4SequenceBarUi(
                barIndex = barIndex,
                label = "Ch ${barIndex + 1}",
                displayName = barNames.getOrElse(barIndex) { defaultBarDisplayName(barIndex) },
                steps = buildSequenceStepsForBar(barIndex = barIndex, steps = steps),
                effectState = barEffectStates.getOrElse(barIndex) { Screen4BarEffectState() },
                isSelectionModeEnabled = selectionState.isSelectionModeEnabled,
                selectedStepIndices = selectionState.selectedStepIndices.sorted(),
            )
        }
    }

    private fun buildSequenceStepsForBar(
        barIndex: Int,
        steps: List<Screen4FavoritePadReference?>,
    ): List<Screen4VisualSlot> {
        return steps.mapIndexed { stepIndex, favoriteReference ->
            val clipId = favoriteReference?.let { reference ->
                reference.clipId ?: favoritePages.firstOrNull { it.pageId == reference.pageId }
                    ?.clipIds?.getOrNull(reference.slotIndex)
            }
            val label = when {
                favoriteReference == null -> "${stepIndex + 1}: ~"
                clipId == null -> "${stepIndex + 1}: Pg${displayPageNumber(favoriteReference.pageId)} Pad${favoriteReference.slotIndex + 1} empty"
                else -> "${stepIndex + 1}: Pg${displayPageNumber(favoriteReference.pageId)} Pad${favoriteReference.slotIndex + 1} ${displayLabelForClipId(clipId)}"
            }
            Screen4VisualSlot(
                index = stepIndex,
                sampleId = clipId,
                label = label,
                sourcePageId = favoriteReference?.pageId,
                sourcePageIndex = favoriteReference?.pageId?.let(::displayPageIndex),
                sourceSlotIndex = favoriteReference?.slotIndex,
            )
        }
    }

    private fun displayLabelForClipId(clipId: String): String {
        return resolveDescriptorByClipId(clipId)?.baseName ?: "saved assignment"
    }

    private fun synchronizeSharedFavorites() {
        val favoritePagesState = sharedFavoritesStore.loadFavoritePagesState(FAVORITE_SLOT_COUNT)
        favoritePages = favoritePagesState.pages.map { page ->
            SharedFavoritePage(
                pageId = page.pageId,
                clipIds = List(FAVORITE_SLOT_COUNT) { slotIndex -> page.assignments[slotIndex] },
            )
        }.ifEmpty {
            listOf(emptyFavoritePage(DEFAULT_FAVORITE_PAGE_ID))
        }
        displayedFavoritePageId = displayedFavoritePageId
            .takeIf { currentPageId -> favoritePages.any { it.pageId == currentPageId } }
            ?: favoritePagesState.selectedPageId
            ?.takeIf { selectedPageId -> favoritePages.any { it.pageId == selectedPageId } }
            ?: favoritePages.first().pageId
    }

    private fun emptySamplePackIndex(): Screen4SamplePackIndex {
        return Screen4SamplePackIndex(
            rootUri = null,
            folderCount = 0,
            sampleCount = 0,
            descriptorsById = emptyMap(),
            descriptorsByClipId = emptyMap(),
        )
    }

    private fun initialRuntimeStatus(index: Screen4SamplePackIndex): String {
        return if (index.rootUri == null) {
            "Use Screen 3 to choose a sample folder and assign favorite pads."
        } else {
            "Drag Screen 3 favorites onto one or more play bars to build a loop."
        }
    }

    private fun restoreWorkingState(state: Screen4WorkingSequenceState?) {
        if (state == null) return
        mutableUiState.value = mutableUiState.value.copy(
            bpm = state.bpm.coerceIn(MIN_BPM, MAX_BPM),
        )
        displayedFavoritePageId = state.displayedFavoritePageId ?: displayedFavoritePageId
        sharedFavoritesStore.saveSelectedFavoritePageId(displayedFavoritePageId)
        replacePlayBars(state.playBars)
        replaceBarEffects(state.barEffects)
        replaceBarNames(state.barNames)
    }

    private fun replacePlayBars(snapshotBars: List<List<Screen4FavoritePadReference?>>) {
        playBars.clear()
        barSelectionStates.clear()
        normalizePlayBarsSnapshot(snapshotBars).forEach { normalizedSteps ->
            playBars += normalizedSteps.toMutableList()
            barSelectionStates += BarSelectionState()
        }
    }

    private fun replaceBarEffects(effectStates: List<Screen4BarEffectState>) {
        barEffectStates.clear()
        repeat(playBars.size) { barIndex ->
            barEffectStates += effectStates.getOrNull(barIndex) ?: Screen4BarEffectState()
        }
    }

    private fun replaceBarNames(names: List<String>) {
        barNames.clear()
        repeat(playBars.size) { barIndex ->
            barNames += names.getOrNull(barIndex)
                ?.trim()
                ?.ifBlank { defaultBarDisplayName(barIndex) }
                ?: defaultBarDisplayName(barIndex)
        }
    }

    private fun normalizePlayBarsSnapshot(
        snapshotBars: List<List<Screen4FavoritePadReference?>>,
    ): List<List<Screen4FavoritePadReference?>> {
        val normalizedBars = snapshotBars
            .take(MAX_PLAY_BAR_COUNT)
            .map(::normalizeStepSnapshot)
        return if (normalizedBars.isNotEmpty()) {
            normalizedBars
        } else {
            listOf(List(STEP_COUNT) { null })
        }
    }

    private fun normalizeStepSnapshot(
        steps: List<Screen4FavoritePadReference?>,
    ): List<Screen4FavoritePadReference?> {
        return List(STEP_COUNT) { stepIndex ->
            steps.getOrNull(stepIndex)?.let { reference ->
                Screen4FavoritePadReference(
                    pageId = reference.pageId,
                    slotIndex = reference.slotIndex.coerceIn(0, FAVORITE_SLOT_COUNT - 1),
                    clipId = reference.clipId,
                )
            }
        }
    }

    private fun snapshotPlayBar(barIndex: Int): List<Screen4FavoritePadReference?> {
        return playBars[barIndex].map { reference ->
            reference?.let {
                Screen4FavoritePadReference(
                    pageId = it.pageId,
                    slotIndex = it.slotIndex,
                    clipId = it.clipId,
                )
            }
        }
    }

    private fun snapshotPlayBars(): List<List<Screen4FavoritePadReference?>> {
        return playBars.indices.map(::snapshotPlayBar)
    }

    private fun snapshotBarEffects(): List<Screen4BarEffectState> {
        return barEffectStates.toList()
    }

    private fun snapshotBarNames(): List<String> {
        return barNames.toList()
    }

    private fun persistWorkingState() {
        sequenceStore.saveWorkingState(
            Screen4WorkingSequenceState(
                bpm = mutableUiState.value.bpm,
                displayedFavoritePageId = displayedFavoritePageId,
                playBars = snapshotPlayBars(),
                barEffects = snapshotBarEffects(),
                barNames = snapshotBarNames(),
            )
        )
    }

    private fun updateBarEffectState(
        barIndex: Int,
        transform: (Screen4BarEffectState) -> Screen4BarEffectState,
    ) {
        val normalizedBarIndex = barIndex.coerceIn(0, barEffectStates.lastIndex)
        val updated = transform(barEffectStates[normalizedBarIndex])
        if (updated == barEffectStates[normalizedBarIndex]) return
        barEffectStates[normalizedBarIndex] = updated
        persistWorkingState()
        applyUiRefresh(
            runtimeStatus = "Updated FX for bar ${normalizedBarIndex + 1}.",
            lastError = null,
        )
        if (mutableUiState.value.isPlaying) {
            queueCurrentLoopForNextCycle()
        } else {
            updateCompileStatusFromVisualLoop()
        }
    }

    private fun nextDefaultBarName(barIndex: Int): String {
        return "Bar ${barIndex + 1} Pattern ${sequenceStore.loadSavedBars().size + 1}"
    }

    private fun defaultBarDisplayName(barIndex: Int): String {
        return "Name${barIndex + 1}"
    }

    private fun nextDefaultSongName(): String {
        return "Song ${sequenceStore.loadSavedSongs().size + 1}"
    }

    private fun currentFavoritePage(): SharedFavoritePage {
        return favoritePages.getOrElse(currentFavoritePageIndex()) {
            favoritePages.firstOrNull() ?: emptyFavoritePage(DEFAULT_FAVORITE_PAGE_ID)
        }
    }

    private fun currentFavoritePageIndex(): Int {
        return favoritePages.indexOfFirst { it.pageId == displayedFavoritePageId }
            .takeIf { it >= 0 }
            ?: 0
    }

    private fun displayPageIndex(pageId: String): Int? {
        return favoritePages.indexOfFirst { it.pageId == pageId }
            .takeIf { it >= 0 }
    }

    private fun displayPageNumber(pageId: String): Int {
        return displayPageIndex(pageId)?.plus(1)
            ?: pageId.substringAfterLast('_', "").toIntOrNull()
            ?: 1
    }

    private fun stepIndicesForBatchTarget(
        barIndex: Int,
        target: Screen4BatchAssignTarget,
    ): List<Int> {
        return when (target) {
            Screen4BatchAssignTarget.ALL_SLOTS -> (0 until STEP_COUNT).toList()
            Screen4BatchAssignTarget.ODD_SLOTS -> (0 until STEP_COUNT).filter { it % 2 == 0 }
            Screen4BatchAssignTarget.EVEN_SLOTS -> (0 until STEP_COUNT).filter { it % 2 == 1 }
            Screen4BatchAssignTarget.SELECTED_SLOTS ->
                barSelectionStates.getOrElse(barIndex) { BarSelectionState() }.selectedStepIndices.sorted()
        }
    }

    private fun batchAssignmentStatus(
        barIndex: Int,
        stepCount: Int,
        favoritePageId: String,
        favoriteSlotIndex: Int,
        target: Screen4BatchAssignTarget,
    ): String {
        val targetLabel = when (target) {
            Screen4BatchAssignTarget.ALL_SLOTS -> "all slots"
            Screen4BatchAssignTarget.ODD_SLOTS -> "odd slots"
            Screen4BatchAssignTarget.EVEN_SLOTS -> "even slots"
            Screen4BatchAssignTarget.SELECTED_SLOTS -> "selected slots"
        }
        return "Assigned $stepCount $targetLabel in bar ${barIndex + 1} from page ${displayPageNumber(favoritePageId)} pad ${favoriteSlotIndex + 1}."
    }

    private fun emptyFavoritePage(pageId: String): SharedFavoritePage {
        return SharedFavoritePage(
            pageId = pageId,
            clipIds = List(FAVORITE_SLOT_COUNT) { null },
        )
    }

    companion object {
        const val DEFAULT_BPM: Int = 120
        const val MIN_BPM: Int = 40
        const val MAX_BPM: Int = 220
        const val DEFAULT_PATTERN: String = ""
        const val DEFAULT_FAVORITE_PAGE_ID: String = Screen3SettingsStore.DEFAULT_FAVORITE_PAGE_ID
        const val FAVORITE_SLOT_COUNT: Int = 9
        const val STEP_COUNT: Int = 16
        const val MIN_PLAY_BAR_COUNT: Int = 1
        const val MAX_PLAY_BAR_COUNT: Int = 5
        const val DEFAULT_PLAY_BAR_COUNT: Int = 5
    }

    private data class SharedFavoritePage(
        val pageId: String,
        val clipIds: List<String?>,
    )

    private data class SavedDependencyIssue(
        val barIndex: Int,
        val stepIndex: Int,
        val pageId: String,
        val slotIndex: Int,
    )

    private data class BarSelectionState(
        val isSelectionModeEnabled: Boolean = false,
        val selectedStepIndices: List<Int> = emptyList(),
    )

    private fun sendForEffectLevel(level: Int): Float {
        return (level.coerceIn(1, 10) / 10f).coerceIn(0f, 1f)
    }

    private fun effectLevelForSend(send: Float): Int {
        return (send.coerceIn(0f, 1f) * 10f).roundToInt().coerceIn(1, 10)
    }

    private fun pitchForEffectLevel(level: Int): Float {
        return (((level.coerceIn(1, 10) - 5.5f) / 4.5f) * 12f).coerceIn(-12f, 12f)
    }

    private fun effectLevelForPitch(pitchSemitones: Float): Int {
        return (((pitchSemitones.coerceIn(-12f, 12f) / 12f) * 4.5f) + 5.5f).roundToInt().coerceIn(1, 10)
    }

    private fun panForEffectLevel(level: Int): Float {
        return (((level.coerceIn(1, 10) - 5.5f) / 4.5f)).coerceIn(-1f, 1f)
    }

    private fun effectLevelForPan(pan: Float): Int {
        return ((pan.coerceIn(-1f, 1f) * 4.5f) + 5.5f).roundToInt().coerceIn(1, 10)
    }
}
