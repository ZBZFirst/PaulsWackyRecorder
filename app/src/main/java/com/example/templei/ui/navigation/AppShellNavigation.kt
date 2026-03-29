package com.example.templei.ui.navigation

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.TextView
import com.example.templei.MainActivity
import com.example.templei.R
import com.example.templei.Screen1Activity
import com.example.templei.Screen2Activity
import com.example.templei.Screen3Activity
import com.example.templei.Screen4Activity

/**
 * Shared Screen 4 style app shell navigation for feature screens.
 */
object AppShellNavigation {
    private val destinations = listOf(
        NavItem(
            containerId = R.id.appNavCapture,
            codeId = R.id.appNavCaptureCode,
            labelId = R.id.appNavCaptureLabel,
            destination = Screen1Activity::class.java,
        ),
        NavItem(
            containerId = R.id.appNavRecorder,
            codeId = R.id.appNavRecorderCode,
            labelId = R.id.appNavRecorderLabel,
            destination = Screen2Activity::class.java,
        ),
        NavItem(
            containerId = R.id.appNavBoard,
            codeId = R.id.appNavBoardCode,
            labelId = R.id.appNavBoardLabel,
            destination = Screen3Activity::class.java,
        ),
        NavItem(
            containerId = R.id.appNavController,
            codeId = R.id.appNavControllerCode,
            labelId = R.id.appNavControllerLabel,
            destination = Screen4Activity::class.java,
        ),
        NavItem(
            containerId = R.id.appNavHome,
            codeId = R.id.appNavHomeCode,
            labelId = R.id.appNavHomeLabel,
            destination = MainActivity::class.java,
        ),
    )

    fun bind(
        activity: Activity,
        currentDestination: Class<out Activity>,
        title: String,
        chipText: String? = null,
    ) {
        activity.findViewById<TextView>(R.id.appHeaderTitle)?.text = title
        activity.findViewById<TextView>(R.id.appHeaderChip)?.apply {
            text = chipText.orEmpty()
            visibility = if (chipText.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        activity.findViewById<View>(R.id.appMenuButton)?.setOnClickListener {
            activity.startActivity(Intent(activity, MainActivity::class.java))
        }

        destinations.forEach { item ->
            val container = activity.findViewById<View>(item.containerId) ?: return@forEach
            val code = activity.findViewById<TextView>(item.codeId)
            val label = activity.findViewById<TextView>(item.labelId)
            val isCurrent = currentDestination == item.destination
            container.background = if (isCurrent) {
                activity.getDrawable(R.drawable.bg_screen4_nav_active)
            } else {
                null
            }
            val textColor = activity.getColor(
                if (isCurrent) {
                    R.color.app_secondary
                } else {
                    R.color.app_nav_inactive
                }
            )
            code?.setTextColor(textColor)
            label?.setTextColor(textColor)
            label?.setTypeface(label.typeface, if (isCurrent) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            container.isEnabled = !isCurrent
            container.setOnClickListener {
                if (!isCurrent) {
                    activity.startActivity(Intent(activity, item.destination))
                }
            }
        }
    }

    private data class NavItem(
        val containerId: Int,
        val codeId: Int,
        val labelId: Int,
        val destination: Class<out Activity>,
    )
}
