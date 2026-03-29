package com.example.templei.feature.soundboard

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.templei.R

class Screen3ClipBrowserRenderer(
    private val context: Context,
    private val clipBrowserContainer: LinearLayout
) {
    data class ClipButtonModel(
        val displayName: String,
        val metaLabel: String,
        val playable: Boolean,
        val payloadId: String
    )

    fun render(
        clips: List<ClipButtonModel>,
        onPlay: (String) -> Unit,
        onAssign: (String) -> Unit
    ) {
        clipBrowserContainer.removeAllViews()
        if (clips.isEmpty()) {
            clipBrowserContainer.addView(
                TextView(context).apply {
                    text = context.getString(R.string.soundboard_browser_empty)
                    setTextColor(context.getColor(R.color.app_text_secondary))
                    textSize = 13f
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }
            )
            return
        }

        clips.chunked(2).forEach { rowClips ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).also { it.bottomMargin = dp(8) }
            }

            repeat(2) { index ->
                val clip = rowClips.getOrNull(index)
                val card = if (clip == null) {
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 0, 1f).also {
                            if (index == 0) it.marginEnd = dp(8)
                        }
                    }
                } else {
                    buildClipCard(clip, onPlay, onAssign, index == 0)
                }
                row.addView(card)
            }

            clipBrowserContainer.addView(row)
        }
    }

    private fun buildClipCard(
        clip: ClipButtonModel,
        onPlay: (String) -> Unit,
        onAssign: (String) -> Unit,
        withEndMargin: Boolean,
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = context.getDrawable(
                if (clip.playable) {
                    R.drawable.bg_screen3_clip_card
                } else {
                    R.drawable.bg_app_section
                }
            )
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ).also {
                if (withEndMargin) {
                    it.marginEnd = dp(8)
                }
            }
            minimumHeight = dp(58)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            isClickable = clip.playable
            isFocusable = clip.playable
            setOnClickListener {
                if (clip.playable) {
                    onPlay(clip.payloadId)
                }
            }

            val textColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                )
            }

            val titleView = TextView(context).apply {
                text = clip.displayName
                setTextColor(context.getColor(R.color.app_text_primary))
                textSize = 12f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val metaView = TextView(context).apply {
                text = clip.metaLabel
                setTextColor(context.getColor(R.color.app_text_secondary))
                textSize = 9f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val assignButton = Button(context).apply {
                text = "+"
                minWidth = 0
                minimumWidth = 0
                minHeight = dp(40)
                minimumHeight = dp(40)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    marginStart = dp(10)
                }
                background = context.getDrawable(R.drawable.bg_app_button_secondary)
                setTextColor(context.getColor(R.color.app_text_secondary))
                textSize = 20f
                setPadding(0, 0, 0, 0)
                setOnClickListener { onAssign(clip.payloadId) }
            }

            textColumn.addView(titleView)
            textColumn.addView(metaView)
            addView(textColumn)
            addView(assignButton)
        }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
