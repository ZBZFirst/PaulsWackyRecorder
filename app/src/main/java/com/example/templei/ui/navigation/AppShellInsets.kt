package com.example.templei.ui.navigation

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Applies Screen 4 style edge-to-edge insets to the shared app shell chrome.
 */
object AppShellInsets {
    fun apply(
        activity: Activity,
        rootId: Int,
        scrollViewId: Int,
        topBarId: Int = com.example.templei.R.id.appTopBar,
        bottomNavId: Int = com.example.templei.R.id.appBottomNav,
    ) {
        val root = activity.findViewById<View>(rootId) ?: return
        val topBar = activity.findViewById<View>(topBarId) ?: return
        val bottomNav = activity.findViewById<View>(bottomNavId) ?: return
        val scrollView = activity.findViewById<View>(scrollViewId) ?: return

        val topBarPaddingStart = topBar.paddingStart
        val topBarPaddingTop = topBar.paddingTop
        val topBarPaddingEnd = topBar.paddingEnd
        val topBarPaddingBottom = topBar.paddingBottom
        val bottomNavPaddingStart = bottomNav.paddingStart
        val bottomNavPaddingTop = bottomNav.paddingTop
        val bottomNavPaddingEnd = bottomNav.paddingEnd
        val bottomNavPaddingBottom = bottomNav.paddingBottom
        val scrollPaddingStart = scrollView.paddingStart
        val scrollPaddingTop = scrollView.paddingTop
        val scrollPaddingEnd = scrollView.paddingEnd
        val scrollPaddingBottom = scrollView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.setPaddingRelative(
                topBarPaddingStart + systemBars.left,
                topBarPaddingTop + systemBars.top,
                topBarPaddingEnd + systemBars.right,
                topBarPaddingBottom,
            )
            bottomNav.setPaddingRelative(
                bottomNavPaddingStart + systemBars.left,
                bottomNavPaddingTop,
                bottomNavPaddingEnd + systemBars.right,
                bottomNavPaddingBottom + systemBars.bottom,
            )
            scrollView.setPaddingRelative(
                scrollPaddingStart + systemBars.left,
                scrollPaddingTop,
                scrollPaddingEnd + systemBars.right,
                scrollPaddingBottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
