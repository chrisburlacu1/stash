package com.example.stash.ui.splash

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.stash.ui.components.rememberMeshClock

/**
 * The emissive spline, as a toy.
 *
 * Tapping the "Stash" title in the top bar opens this. It is deliberately **not** part of the app's
 * visual language and carries no state — there is nothing to signal, nothing resolves, and no
 * surface hands off to it. Everywhere else in Stash a gradient means something specific (the card
 * mesh *is* the model deciding; the chat aura *is* the assistant working), and this is the one
 * exception, kept honest by being unreachable except on purpose.
 *
 * It lives in its own [Dialog] window for exactly that reason: it composes over the app without
 * touching the feed's layout, its own back handling dismisses it, and when it closes nothing of it
 * remains — the `RuntimeShader` goes with the window and [rememberSplineClock] stops. The app does
 * not have to be reshaped to host a joke.
 *
 * This started as a launch splash. That was abandoned: nothing at startup is slow enough to hide, so
 * the field was pure dead time on every cold start, and it double-splashed with the system's icon
 * window (~557ms cold before the first frame even lands). As an easter egg the same shader costs
 * nothing until asked for.
 */
@Composable
fun SplineEasterEgg(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            // Full-bleed: the composition is measured from the bottom edge outward and is built to
            // fill whatever it is given, so a dialog inset would crop the wave rather than scale it.
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            // Without this the dialog gets the platform's dim scrim *and* its own insets, so the
            // field stopped short of the status and navigation bars and sat on a grey wash.
            decorFitsSystemWindows = false,
        ),
    ) {
        // A Dialog gets its own window, which does not inherit the activity's enableEdgeToEdge() —
        // so it must be told to draw behind the system bars itself, and to drop the default dim.
        // Without this the field is letterboxed by the bars it is supposed to run underneath.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { window ->
                window.setDimAmount(0f)
                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                )
                WindowCompat.setDecorFitsSystemWindows(window, false)
                // The plate is dark, so the system bar icons must be light for its duration or the
                // clock is dark-on-dark. Scoped to this window only — the activity's own bars are
                // untouched, which is what makes this safe here and awkward when it was a splash.
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
        SplineShowpiece(onDismiss = onDismiss)
    }
}

/**
 * Fades in, runs until dismissed, fades out.
 *
 * Tap anywhere or press back to leave. `exiting` runs the same withdrawal the field was built with —
 * opacity down while the wave sinks past the bottom edge — so it leaves the way it arrived instead of
 * blinking out.
 */
@Composable
private fun SplineShowpiece(onDismiss: () -> Unit) {
    val palette = remember { splashPalette() }
    val enter = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    var dismissing by remember { mutableStateOf(false) }

    // Runs for the life of the window and stops with it — the whole reason this is a dialog.
    val clock by rememberMeshClock(running = true)

    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(durationMillis = 420))
    }

    LaunchedEffect(dismissing) {
        if (dismissing) {
            exit.animateTo(1f, tween(durationMillis = 380))
            onDismiss()
        }
    }

    BackHandler(enabled = !dismissing) { dismissing = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = enter.value * (1f - exit.value) }
            .splineField(
                palette = palette,
                time = { clock },
                // The entrance rises from the sunk position and the exit sinks back to it, so one
                // uniform serves both — `reveal` is "how far withdrawn", whichever direction it is
                // travelling.
                reveal = { (1f - enter.value) + exit.value },
            )
            .pointerInput(Unit) {
                detectTapGestures { dismissing = true }
            },
    )
}
