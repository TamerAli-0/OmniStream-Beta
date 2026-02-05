package com.omnistream.ui.utils

import android.app.Activity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Modifier that hides system navigation bars when:
 * - User scrolls content
 * - User taps in the middle area of the screen
 */
fun Modifier.hideSystemBarsOnInteraction(): Modifier = composed {
    val context = LocalContext.current
    val activity = context as? Activity

    // Nested scroll connection to detect scrolling
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Hide nav buttons when user starts scrolling
                activity?.let { act ->
                    val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                    windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Hide nav buttons when user flings
                activity?.let { act ->
                    val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                    windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
                }
                return Velocity.Zero
            }
        }
    }

    this
        .nestedScroll(scrollConnection)
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { offset ->
                    // Only hide if tapping in the middle area (not edges)
                    val screenHeight = size.height
                    val screenWidth = size.width
                    val tapY = offset.y
                    val tapX = offset.x

                    // Define middle area (20% from edges)
                    val topMargin = screenHeight * 0.2f
                    val bottomMargin = screenHeight * 0.8f
                    val leftMargin = screenWidth * 0.2f
                    val rightMargin = screenWidth * 0.8f

                    if (tapY > topMargin && tapY < bottomMargin &&
                        tapX > leftMargin && tapX < rightMargin) {
                        // Tap in middle area - hide nav buttons
                        activity?.let { act ->
                            val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                            windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
                        }
                    }
                }
            )
        }
}

/**
 * Simple modifier that hides nav bars on any tap (not just middle)
 */
fun Modifier.hideSystemBarsOnTap(): Modifier = composed {
    val context = LocalContext.current
    val activity = context as? Activity

    this.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                activity?.let { act ->
                    val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                    windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
                }
            }
        )
    }
}
