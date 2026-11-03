package com.pos.kiosk.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

/**
 * Width of the window this composition is drawn in, in dp. Use it instead of
 * `LocalConfiguration.current.screenWidthDp`, which is the whole screen (wrong in split screen /
 * freeform windows) and only updates on configuration changes.
 *
 * Inside a `Dialog` this is the dialog's own window, so read it in the caller's composition (before
 * `Dialog { }`) when a decision should follow the main window, and pass the value in.
 */
@Composable
fun currentWindowWidth(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }

/** Height of the window this composition is drawn in, in dp. Same caveat inside a `Dialog` as [currentWindowWidth]. */
@Composable
fun currentWindowHeight(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
