package com.shawady.bodywise.ui.components

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** The activity's own view, provided at the app root (a Dialog's LocalView is the dialog's window, not the activity's). */
val LocalMainView = compositionLocalOf<View?> { null }

/**
 * Height of the system navigation bar. Full-screen dialogs can extend underneath it on some phones (Realme and others),
 * and inside a dialog window Compose's own insets come back as zero, so bottom buttons end up half hidden.
 * Reading the activity's root insets is reliable whether or not the activity is edge-to-edge.
 */
@Composable
fun dialogBottomInset(): Dp {
    val view = LocalMainView.current ?: return 0.dp
    val density = LocalDensity.current
    val px = ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    return with(density) { px.toDp() }
}
