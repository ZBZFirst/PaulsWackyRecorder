package com.example.templei.feature.soundboard

import android.app.AlertDialog
import android.content.Context
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.example.templei.R

class Screen3SettingsDialogHelper(private val context: Context) {

    fun show(
        config: SoundboardConfig,
        onReset: () -> Unit,
        onApply: (SoundboardConfig) -> Unit
    ) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }

        fun rowLabel(text: String): TextView = TextView(context).apply { this.text = text }

        val maxStreamsLabel = rowLabel(context.getString(R.string.soundboard_settings_max_streams, config.maxStreams))
        val maxStreamsSeek = SeekBar(context).apply {
            max = 7
            progress = (config.maxStreams - 1).coerceIn(0, 7)
            setOnSeekBarChangeListener(simpleSeekListener { _, value ->
                maxStreamsLabel.text = context.getString(R.string.soundboard_settings_max_streams, value + 1)
            })
        }

        val cooldownLabel = rowLabel(context.getString(R.string.soundboard_settings_cooldown_ms, config.cooldownMs.toInt()))
        val cooldownSeek = SeekBar(context).apply {
            max = 10
            progress = (config.cooldownMs / 50L).toInt().coerceIn(0, 10)
            setOnSeekBarChangeListener(simpleSeekListener { _, value ->
                cooldownLabel.text = context.getString(R.string.soundboard_settings_cooldown_ms, value * 50)
            })
        }

        val cacheLabel = rowLabel(context.getString(R.string.soundboard_settings_cache_size, config.maxCacheSize))
        val cacheSeek = SeekBar(context).apply {
            max = 28
            progress = (config.maxCacheSize - 8).coerceIn(0, 28)
            setOnSeekBarChangeListener(simpleSeekListener { _, value ->
                cacheLabel.text = context.getString(R.string.soundboard_settings_cache_size, value + 8)
            })
        }

        val unloadSwitch = CheckBox(context).apply {
            text = context.getString(R.string.soundboard_settings_unload_on_folder_change)
            isChecked = config.unloadOnFolderChange
        }

        val cachePolicyLabel = rowLabel(context.getString(R.string.soundboard_settings_cache_policy))
        val cachePolicyGroup = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
        }
        val aggressiveRadio = RadioButton(context).apply {
            id = CACHE_POLICY_AGGRESSIVE_ID
            text = context.getString(R.string.soundboard_settings_cache_policy_aggressive)
        }
        val balancedRadio = RadioButton(context).apply {
            id = CACHE_POLICY_BALANCED_ID
            text = context.getString(R.string.soundboard_settings_cache_policy_balanced)
        }
        val stickyRadio = RadioButton(context).apply {
            id = CACHE_POLICY_STICKY_ID
            text = context.getString(R.string.soundboard_settings_cache_policy_sticky)
        }
        cachePolicyGroup.addView(aggressiveRadio)
        cachePolicyGroup.addView(balancedRadio)
        cachePolicyGroup.addView(stickyRadio)
        cachePolicyGroup.check(
            when (config.cachePolicy) {
                CachePolicy.AGGRESSIVE -> CACHE_POLICY_AGGRESSIVE_ID
                CachePolicy.BALANCED -> CACHE_POLICY_BALANCED_ID
                CachePolicy.STICKY -> CACHE_POLICY_STICKY_ID
            }
        )

        root.addView(maxStreamsLabel)
        root.addView(maxStreamsSeek)
        root.addView(cooldownLabel)
        root.addView(cooldownSeek)
        root.addView(cacheLabel)
        root.addView(cacheSeek)
        root.addView(unloadSwitch)
        root.addView(cachePolicyLabel)
        root.addView(cachePolicyGroup)

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.soundboard_settings_title))
            .setView(root)
            .setNeutralButton(context.getString(R.string.soundboard_settings_reset)) { _, _ ->
                onReset()
            }
            .setPositiveButton(context.getString(R.string.soundboard_settings_apply)) { _, _ ->
                val policy = when (cachePolicyGroup.checkedRadioButtonId) {
                    CACHE_POLICY_AGGRESSIVE_ID -> CachePolicy.AGGRESSIVE
                    CACHE_POLICY_STICKY_ID -> CachePolicy.STICKY
                    else -> CachePolicy.BALANCED
                }
                val updated = SoundboardConfig(
                    maxStreams = (maxStreamsSeek.progress + 1).coerceIn(1, 8),
                    cooldownMs = (cooldownSeek.progress * 50L).coerceIn(0L, 500L),
                    maxCacheSize = (cacheSeek.progress + 8).coerceIn(8, 36),
                    unloadOnFolderChange = unloadSwitch.isChecked,
                    cachePolicy = policy
                )
                onApply(updated)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun simpleSeekListener(onChange: (SeekBar, Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                onChange(seekBar, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        }
    }

    private companion object {
        private const val CACHE_POLICY_AGGRESSIVE_ID = 1001
        private const val CACHE_POLICY_BALANCED_ID = 1002
        private const val CACHE_POLICY_STICKY_ID = 1003
    }
}
