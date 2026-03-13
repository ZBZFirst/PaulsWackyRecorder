package com.example.templei.feature.soundboard

import android.widget.Button

class Screen3FavoritePadHelper {
    fun bind(
        buttons: List<Button>,
        onTapSlot: (Int) -> Unit,
        onLongPressSlot: (Int) -> Unit
    ) {
        buttons.forEachIndexed { index, button ->
            button.setOnClickListener { onTapSlot(index) }
            button.setOnLongClickListener {
                onLongPressSlot(index)
                true
            }
        }
    }

    fun renderLabels(buttons: List<Button>, labels: List<String>) {
        buttons.forEachIndexed { index, button ->
            button.text = labels.getOrElse(index) { "" }
        }
    }
}
