package com.example.templei.feature.screen4

import java.util.Locale

/**
 * Compiles parsed pattern AST into cycle-relative sample events.
 */
class Screen4PatternCompiler(
    private val sampleLibraryRepository: Screen4SampleLibraryRepository,
) {
    fun compile(
        source: String,
        samplePackIndex: Screen4SamplePackIndex,
        bpm: Int,
    ): Result<Screen4CompiledPattern> {
        if (samplePackIndex.rootUri == null) {
            return Result.failure(IllegalArgumentException("Choose a sample folder before compiling a pattern."))
        }
        return Screen4PatternParser()
            .parse(source)
            .mapCatching { parsed ->
                val flattened = flatten(parsed.ast)
                if (flattened.isEmpty()) {
                    throw IllegalArgumentException("Pattern does not contain any playable steps.")
                }
                val cycleDurationMs = (4 * 60_000L) / bpm.coerceAtLeast(1)
                val resolvedById = mutableMapOf<String, Screen4SampleDescriptor>()
                val scheduledEvents = flattened.mapIndexedNotNull { index, node ->
                    when (node) {
                        is Screen4PatternNode.Rest -> null
                        is Screen4PatternNode.Event -> {
                            val descriptor = sampleLibraryRepository
                                .resolveSample(node.sampleId, samplePackIndex)
                                .getOrElse { throw it }
                            resolvedById[descriptor.sampleId.lowercase(Locale.US)] = descriptor
                            Screen4ScheduledEvent(
                                sampleId = descriptor.sampleId,
                                stepIndex = index,
                                offsetMs = (index * cycleDurationMs.toDouble() / flattened.size).toLong(),
                                gain = node.params.gain.coerceIn(0f, 1f),
                                pan = node.params.pan.coerceIn(-1f, 1f),
                                speed = node.params.speed.coerceIn(0.5f, 2f),
                            )
                        }
                        else -> throw IllegalStateException("Unexpected node after flattening.")
                    }
                }
                Screen4CompiledPattern(
                    sourceText = source,
                    stepsPerCycle = flattened.size,
                    scheduledEvents = scheduledEvents,
                    resolvedSamples = resolvedById,
                )
            }
    }

    private fun flatten(node: Screen4PatternNode): List<Screen4PatternNode> {
        return when (node) {
            is Screen4PatternNode.Event -> listOf(node)
            is Screen4PatternNode.Rest -> listOf(node)
            is Screen4PatternNode.Sequence -> node.children.flatMap(::flatten)
            is Screen4PatternNode.Repeat -> List(node.count) { flatten(node.node) }.flatten()
        }
    }
}
