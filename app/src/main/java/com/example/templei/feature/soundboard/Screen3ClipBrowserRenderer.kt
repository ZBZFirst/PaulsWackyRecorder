package com.example.templei.feature.soundboard

import android.content.Context
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
        val playable: Boolean,
        val payloadId: String
    )

    fun render(
        clips: List<ClipButtonModel>,
        onTap: (String) -> Unit,
        onLongPress: (String) -> Unit
    ) {
        clipBrowserContainer.removeAllViews()
        if (clips.isEmpty()) {
            clipBrowserContainer.addView(TextView(context).apply { text = context.getString(R.string.soundboard_browser_empty) })
            return
        }

        clips.chunked(3).forEach { rowClips ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = 8 }
            }

            repeat(3) { index ->
                val clip = rowClips.getOrNull(index)
                val button = Button(context).apply {
                    isAllCaps = false
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also {
                        it.marginEnd = if (index < 2) 8 else 0
                    }
                }

                if (clip == null) {
                    button.isEnabled = false
                    button.text = ""
                    button.visibility = Button.INVISIBLE
                } else {
                    button.text = if (clip.playable) {
                        clip.displayName
                    } else {
                        "${clip.displayName} (${context.getString(R.string.soundboard_unplayable_suffix)})"
                    }
                    button.setOnClickListener { onTap(clip.payloadId) }
                    button.setOnLongClickListener {
                        onLongPress(clip.payloadId)
                        true
                    }
                }
                row.addView(button)
            }
            clipBrowserContainer.addView(row)
        }
    }
}
