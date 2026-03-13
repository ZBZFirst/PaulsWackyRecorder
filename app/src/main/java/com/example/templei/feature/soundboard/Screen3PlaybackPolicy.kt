package com.example.templei.feature.soundboard

/**
 * Playback guardrail policy for Screen 3.
 *
 * This class is intentionally Android-free so it can be unit tested and reused
 * by any screen hosting the soundboard interaction model.
 */
class Screen3PlaybackPolicy {

    data class Input(
        val clipId: String,
        val isPlayable: Boolean,
        val nowMs: Long,
        val lastPlayedAtMs: Long?,
        val activeStreams: Int,
        val config: SoundboardConfig
    )

    sealed interface Decision {
        data object Accept : Decision
        data class Reject(val reason: SoundboardStateMachine.PlaybackRejectionReason) : Decision
    }

    /**
     * Applies button-driven playback constraints in deterministic order.
     *
     * Constraint order mirrors current Screen 3 behavior:
     * 1) clip must be playable,
     * 2) cooldown must not be active,
     * 3) max stream cap must not be exceeded.
     */
    fun evaluate(input: Input): Decision {
        if (!input.isPlayable) {
            return Decision.Reject(SoundboardStateMachine.PlaybackRejectionReason.CLIP_NOT_PLAYABLE)
        }

        val lastPlayed = input.lastPlayedAtMs ?: 0L
        if (input.nowMs - lastPlayed < input.config.cooldownMs) {
            return Decision.Reject(SoundboardStateMachine.PlaybackRejectionReason.COOLDOWN_ACTIVE)
        }

        if (input.activeStreams >= input.config.maxStreams) {
            return Decision.Reject(SoundboardStateMachine.PlaybackRejectionReason.MAX_STREAMS_REACHED)
        }

        return Decision.Accept
    }
}
