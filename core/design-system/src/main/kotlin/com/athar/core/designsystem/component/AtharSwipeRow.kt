package com.athar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.athar.core.designsystem.theme.AtharTheme

/**
 * Row wrapper that runs [onConfirm] on a leading-edge swipe (toward start-of-text) and
 * [onDismiss] on a trailing-edge swipe (toward end-of-text). Backgrounds are ember and
 * crimson respectively — the only place crimson appears outside destructive-confirm dialogs.
 *
 * Layout-direction aware: in RTL, "swipe right" maps to the same logical direction as
 * "swipe left" in LTR. The brief specifies confirm = right in LTR; we map that to
 * `StartToEnd` so both reading directions feel consistent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtharSwipeRow(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val theme = AtharTheme
    val state = rememberSwipeToDismissBoxState()

    LaunchedEffect(state) {
        snapshotFlow { state.currentValue }.collect { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onConfirm()
                    state.reset()
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDismiss()
                    state.reset()
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val (color, label, align) = when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(theme.colors.ember, "تأكيد", Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(theme.colors.crimson, "تجاهل", Alignment.CenterEnd)
                SwipeToDismissBoxValue.Settled -> Triple(theme.colors.parchment, "", Alignment.Center)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(theme.spacing.m),
                contentAlignment = align,
            ) {
                if (label.isNotEmpty()) {
                    AtharText(text = label, color = theme.colors.parchment, style = theme.typography.headline)
                }
            }
        },
        content = { content() },
    )
}
