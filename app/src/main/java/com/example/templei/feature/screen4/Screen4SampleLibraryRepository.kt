package com.example.templei.feature.screen4

import android.net.Uri
import com.example.templei.feature.soundboard.ClipIndexRepository
import java.util.Locale

/**
 * Folder-based sample library adapter for Screen 4 scheduling.
 */
class Screen4SampleLibraryRepository(
    private val clipIndexRepository: ClipIndexRepository,
) {
    fun currentRootUri(): Uri? = clipIndexRepository.getPersistedRootUri()

    fun setRootUri(uri: Uri) {
        clipIndexRepository.setPersistedRootUri(uri)
    }

    fun rebuildIndex(uri: Uri): Screen4SamplePackIndex {
        setRootUri(uri)
        clipIndexRepository.rebuildIndex(uri)
        return loadIndex()
    }

    fun refreshIndex(): Screen4SamplePackIndex {
        val rootUri = currentRootUri() ?: return emptyIndex()
        clipIndexRepository.rebuildIndex(rootUri)
        return loadIndex()
    }

    fun loadIndex(): Screen4SamplePackIndex {
        val rootUri = currentRootUri() ?: return emptyIndex()
        val indexedClips = clipIndexRepository.getAllIndexedClips()
        val folderNames = indexedClips
            .map { it.folderName }
            .distinct()
            .sorted()
        val descriptors = indexedClips
            .asSequence()
            .filter { clip ->
                clip.playable && clip.fileName.endsWith(".wav", ignoreCase = true)
            }
            .map { clip ->
                val baseName = clip.fileName.substringBeforeLast(".")
                val sampleId = "${clip.folderName}/$baseName"
                Screen4SampleDescriptor(
                    sampleId = sampleId,
                    clipId = clip.clipId,
                    uri = Uri.parse(clip.clipUri),
                    folderName = clip.folderName,
                    displayName = clip.fileName,
                    baseName = baseName,
                    durationMs = clip.durationMs,
                )
            }
            .toList()
        return Screen4SamplePackIndex(
            rootUri = rootUri,
            folderCount = folderNames.size,
            sampleCount = descriptors.size,
            descriptorsById = descriptors.associateBy { it.sampleId.lowercase(Locale.US) },
            descriptorsByClipId = descriptors.associateBy { it.clipId },
        )
    }

    fun resolveSample(
        sampleName: String,
        index: Screen4SamplePackIndex,
    ): Result<Screen4SampleDescriptor> {
        val normalized = sampleName.trim().lowercase(Locale.US)
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("Sample name cannot be blank."))
        }

        index.descriptorsById[normalized]?.let { return Result.success(it) }

        val basenameMatches = index.descriptorsById.values.filter { descriptor ->
            descriptor.baseName.equals(sampleName, ignoreCase = true)
        }
        return when {
            basenameMatches.isEmpty() ->
                Result.failure(IllegalArgumentException("Unknown sample '$sampleName'."))
            basenameMatches.size > 1 ->
                Result.failure(
                    IllegalArgumentException(
                        "Sample '$sampleName' is ambiguous. Use folder/sample form, e.g. ${basenameMatches.first().sampleId}."
                    )
                )
            else -> Result.success(basenameMatches.first())
        }
    }

    private fun emptyIndex(): Screen4SamplePackIndex {
        return Screen4SamplePackIndex(
            rootUri = null,
            folderCount = 0,
            sampleCount = 0,
            descriptorsById = emptyMap(),
            descriptorsByClipId = emptyMap(),
        )
    }
}
