package com.example.templei.feature.soundboard

import android.widget.Button
import com.example.templei.R

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

    fun renderSlots(
        buttons: List<Button>,
        items: List<Screen3FavoriteSlotItem>,
        selectedSlotIndex: Int,
    ) {
        buttons.forEachIndexed { index, button ->
            val item = items.getOrNull(index)
            val isAssigned = item?.assignedClipId != null
            val isSelected = index == selectedSlotIndex
            button.text = when {
                item == null -> ""
                isAssigned -> item.label
                else -> "${item.label}\n+"
            }
            button.background = button.context.getDrawable(
                when {
                    isAssigned -> R.drawable.bg_screen3_favorite_tile_assigned
                    else -> R.drawable.bg_screen3_favorite_tile_empty
                }
            )
            button.setTextColor(
                button.context.getColor(
                    when {
                        isAssigned -> R.color.app_secondary
                        isSelected -> R.color.app_primary
                        else -> R.color.app_text_secondary
                    }
                )
            )
            button.alpha = if (isAssigned || isSelected) 1f else 0.9f
        }
    }
}
