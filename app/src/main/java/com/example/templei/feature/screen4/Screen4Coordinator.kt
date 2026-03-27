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
    private var favoritePages: List<SharedFavoritePage> = listOf(emptyFavoritePage(DEFAULT_FAVORITE_PAGE_ID))
    private var displayedFavoritePageId: String = DEFAULT_FAVORITE_PAGE_ID

    private val mutableUiState = MutableStateFlow(
        Screen4UiState(
            currentFavoritePageId = DEFAULT_FAVORITE_PAGE_ID,
            favoriteSlots = buildFavoriteSlots(),
            sequenceBars = buildSequenceBars(activeStepIndex = 0, isPlaying = false),
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
            sequenceBars = buildSequenceBars(activeStepIndex = 0, isPlaying = false),
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

    fun addPlayBar() {
        if (playBars.size >= MAX_PLAY_BAR_COUNT) {
            mutableUiState.value = mutableUiState.value.copy(
                runtimeStatus = "Screen 4 supports up to $MAX_PLAY_BAR_COUNT play bars.",
                lastError = null,
            )
            return
        }
        playBars += MutableList(STEP_COUNT) { null as Screen4FavoritePadReference? }
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

    fun updateBpm(rawValue: String) {
        val parsed = rawValue.toIntOrNull()?.coerceIn(MIN_BPM, MAX_BPM) ?: return
        mutableUiState.value = mutableUiState.value.copy(bpm = parsed)
        persistWorkingState()
        if (mutableUiState.value.isPlaying) {
            queueCurrentLoopForNextCycle()
        } else {
            updateCompileStatusFromVisualLoop()
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

    fun savedBarSnapshots(): List<Screen4SavedBarSnapshot> {
        return sequenceStore.loadSavedBars()
    }

    fun savePlayBar(
        barIndex: Int,
        requestedName: String,
    ) {
        val normalizedBarIndex = barIndex.coerceIn(0, playBars.lastIndex)
        val normalizedName = requestedName.trim().ifBlank { nextDefaultBarName(normalizedBarIndex) }
        val replacedExisting = sequenceStore.saveBar(
            Screen4SavedBarSnapshot(
                name = normalizedName,
                savedAtMs = System.currentTimeMillis(),
                steps = snapshotPlayBar(normalizedBarIndex),
            )
        )
        mutableUiState.value = mutableUiState.value.copy(
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
        val snapshot = sequenceStore.loadSavedBars()
            .firstOrNull { it.name.equals(savedName, ignoreCase = true) }
        if (snapshot == null) {
            mutableUiState.value = mutableUiState.value.copy(
                runtimeStatus = "Saved bar \"$savedName\" was not found.",
                lastError = "Saved bar \"$savedName\" was not found.",
            )
            return
        }
        playBars[normalizedBarIndex] = normalizeStepSnapshot(snapshot.steps).toMutableList()
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
        val snapshot = sequenceStore.loadSavedSongs()
            .firstOrNull { it.name.equals(savedName, ignoreCase = true) }
        if (snapshot == null) {
            mutableUiState.value = mutableUiState.value.copy(
                runtimeStatus = "Saved song \"$savedName\" was not found.",
                lastError = "Saved song \"$savedName\" was not found.",
            )
            return
        }
        mutableUiState.value = mutableUiState.value.copy(
            bpm = snapshot.bpm.coerceIn(MIN_BPM, MAX_BPM),
        )
        displayedFavoritePageId = snapshot.displayedFavoritePageId ?: displayedFavoritePageId
        sharedFavoritesStore.saveSelectedFavoritePageId(displayedFavoritePageId)
        replacePlayBars(snapshot.playBars)
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

    fun play() {
        scope.launch {
            val compiled = buildVisualPattern().getOrElse {
                mutableUiState.value = mutableUiState.value.copy(
                    transportState = Screen4TransportState.ERROR,
                    runtimeStatus = "Play blocked. Assign Screen 3 pads to at least one step first.",
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
                sequenceBars = buildSequenceBars(activeStepIndex = 0, isPlaying = false),
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
            sequenceBars = buildSequenceBars(activeStepIndex = 0, isPlaying = true),
        )
    }

    fun onStepTick(stepIndex: Int) {
        mutableUiState.value = mutableUiState.value.copy(
            activeStepIndex = stepIndex,
            sequenceBars = buildSequenceBars(activeStepIndex = stepIndex, isPlaying = true),
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
            val compiled = buildVisualPattern().getOrElse {
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

            val cycleDurationMs = (4 * 60_000L) / mutableUiState.value.bpm.coerceAtLeast(1)
            val resolvedSamples = mutableMapOf<String, Screen4SampleDescriptor>()
            val scheduledEvents = populatedSteps.map { (_, indexedStep) ->
                val stepIndex = indexedStep.first
                val favoriteReference = indexedStep.second
                val favoritePage = favoritePages.firstOrNull { it.pageId == favoriteReference.pageId }
                val clipId = favoriteReference.clipId
                    ?: favoritePage?.clipIds?.getOrNull(favoriteReference.slotIndex)
                    ?: throw IllegalArgumentException(
                        "Page ${displayPageNumber(favoriteReference.pageId)} pad ${favoriteReference.slotIndex + 1} is empty."
                    )
                val descriptor = resolveDescriptorByClipId(clipId)
                    ?: throw IllegalArgumentException(
                        "Saved clip for page ${displayPageNumber(favoriteReference.pageId)} pad ${favoriteReference.slotIndex + 1} is no longer available."
                    )
                resolvedSamples[descriptor.sampleId.lowercase(Locale.US)] = descriptor
                Screen4ScheduledEvent(
                    sampleId = descriptor.sampleId,
                    stepIndex = stepIndex,
                    offsetMs = (stepIndex * cycleDurationMs.toDouble() / STEP_COUNT).toLong(),
                    gain = 1f,
                    pan = 0f,
                    speed = 1f,
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
            sequenceBars = buildSequenceBars(
                activeStepIndex = mutableUiState.value.activeStepIndex,
                isPlaying = mutableUiState.value.isPlaying,
            ),
            runtimeStatus = runtimeStatus,
            lastError = lastError,
        )
    }

    private fun buildFavoriteSlots(): List<Screen4VisualSlot> {
        return currentFavoritePage().clipIds.mapIndexed { index, sampleId ->
            Screen4VisualSlot(
                index = index,
                sampleId = sampleId,
                label = sampleId?.let { "Pad ${index + 1}\n${displayLabelForClipId(it)}" }
                    ?: "Pad ${index + 1}\n(set in Screen 3)",
            )
        }
    }

    private fun buildSequenceBars(
        activeStepIndex: Int,
        isPlaying: Boolean,
    ): List<Screen4SequenceBarUi> {
        return playBars.mapIndexed { barIndex, steps ->
            Screen4SequenceBarUi(
                barIndex = barIndex,
                label = "Bar ${barIndex + 1}",
                steps = buildSequenceStepsForBar(
                    barIndex = barIndex,
                    steps = steps,
                    activeStepIndex = activeStepIndex,
                    isPlaying = isPlaying,
                ),
            )
        }
    }

    private fun buildSequenceStepsForBar(
        barIndex: Int,
        steps: List<Screen4FavoritePadReference?>,
        activeStepIndex: Int,
        isPlaying: Boolean,
    ): List<Screen4VisualSlot> {
        return steps.mapIndexed { stepIndex, favoriteReference ->
            val prefix = if (stepIndex == activeStepIndex && isPlaying) ">" else ""
            val clipId = favoriteReference?.let { reference ->
                reference.clipId ?: favoritePages.firstOrNull { it.pageId == reference.pageId }
                    ?.clipIds?.getOrNull(reference.slotIndex)
            }
            val label = when {
                favoriteReference == null -> "$prefix${stepIndex + 1}: ~"
                clipId == null -> "$prefix${stepIndex + 1}: Pg${displayPageNumber(favoriteReference.pageId)} Pad${favoriteReference.slotIndex + 1} empty"
                else -> "$prefix${stepIndex + 1}: Pg${displayPageNumber(favoriteReference.pageId)} Pad${favoriteReference.slotIndex + 1} ${displayLabelForClipId(clipId)}"
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
    }

    private fun replacePlayBars(snapshotBars: List<List<Screen4FavoritePadReference?>>) {
        playBars.clear()
        normalizePlayBarsSnapshot(snapshotBars).forEach { normalizedSteps ->
            playBars += normalizedSteps.toMutableList()
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

    private fun persistWorkingState() {
        sequenceStore.saveWorkingState(
            Screen4WorkingSequenceState(
                bpm = mutableUiState.value.bpm,
                displayedFavoritePageId = displayedFavoritePageId,
                playBars = snapshotPlayBars(),
            )
        )
    }

    private fun nextDefaultBarName(barIndex: Int): String {
        return "Bar ${barIndex + 1} Pattern ${sequenceStore.loadSavedBars().size + 1}"
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
        const val DEFAULT_PLAY_BAR_COUNT: Int = 1
    }

    private data class SharedFavoritePage(
        val pageId: String,
        val clipIds: List<String?>,
    )
}
