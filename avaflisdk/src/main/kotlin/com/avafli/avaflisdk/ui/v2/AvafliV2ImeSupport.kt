package com.avafli.avaflisdk.ui.v2

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//
// Shared IME plumbing for every text-input screen inside the experience
// drawer (3.1 keyboard audit). The drawer's host activity pins the window to
// decorFitsSystemWindows(false) + ADJUST_RESIZE, so Compose receives the IME
// inset directly — these helpers turn that inset into the actual guarantee:
//
//  1. the focused field always scrolls visible ABOVE the keyboard (on focus,
//     on ImeAction.Next focus moves, and as the IME animates in);
//  2. everything below the fold — CTAs, resend links, legal footers — stays
//     reachable by scrolling while the keyboard is open;
//  3. tapping empty background clears focus (keyboard dismisses), and hiding
//     the IME leaves no stale gap (imePadding tracks the animated inset).
//

/**
 * Beat between a field gaining focus and the bring-into-view request, so the
 * IME inset has landed and the request targets the post-resize viewport.
 */
internal const val Avafli_IME_SETTLE_MS = 180L

/**
 * The one blessed scroll-container order for input screens:
 * `imePadding()` BEFORE `verticalScroll()`, so the keyboard shrinks the
 * scroll VIEWPORT rather than merely padding the content.
 *
 * The distinction is what makes [BringIntoViewRequester] work: with the
 * padding inside the scrollable (the pre-3.1 order), the viewport still
 * extends under the keyboard, so a keyboard-covered field counts as
 * "already visible" and `bringIntoView()` is a no-op. With the viewport
 * ending above the keyboard, the request actually scrolls the field clear —
 * and all remaining content stays reachable by scrolling.
 */
internal fun Modifier.avafliImeScrollable(state: ScrollState): Modifier = this
    .imePadding()
    .verticalScroll(state)

/**
 * Android-convention keyboard dismissal: a tap on empty background clears
 * focus, which closes the IME. Apply to a screen's root container — taps
 * consumed by children (fields, buttons, links, checkboxes) never reach it.
 */
@Composable
internal fun Modifier.avafliClearFocusOnTap(): Modifier {
    val focusManager = LocalFocusManager.current
    return this.pointerInput(Unit) {
        detectTapGestures(onTap = { focusManager.clearFocus() })
    }
}

/**
 * Keeps the field carrying [bringIntoView] visible above the keyboard while
 * it holds focus:
 *
 *  - on focus gain, one request after [Avafli_IME_SETTLE_MS] (the original
 *    2.9 behavior — covers the taps that don't move the IME inset);
 *  - on every IME-inset change while focused (the keyboard animating in,
 *    growing a suggestion strip, or switching layouts), so the request
 *    tracks the FINAL keyboard height instead of a mid-animation guess.
 *
 * Focus moves between fields (ImeAction.Next / tap) re-run the same path,
 * so the newly focused field scrolls clear too.
 *
 * [onFocusChanged] preserves each call site's own focus bookkeeping
 * (touched-state gating, autocomplete open/close).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.avafliBringIntoViewOnFocus(
    bringIntoView: BringIntoViewRequester,
    onFocusChanged: ((Boolean) -> Unit)? = null,
): Modifier {
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    // Reading the inset in composition recomposes per animation frame — the
    // effect restarts with it, so the last request targets the settled inset.
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom, focused) {
        if (focused && imeBottom > 0) bringIntoView.bringIntoView()
    }
    return this.onFocusEvent { state ->
        focused = state.isFocused
        onFocusChanged?.invoke(state.isFocused)
        if (state.isFocused) {
            scope.launch {
                delay(Avafli_IME_SETTLE_MS)
                bringIntoView.bringIntoView()
            }
        }
    }
}
