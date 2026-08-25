package com.avafli.avaflisdk.ui.v2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.avaflisdk.network.AvafliPlaceAddress
import com.avafli.avaflisdk.network.AvafliPlaceSuggestion
import com.avafli.avaflisdk.network.AvafliPlacesClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//
// Google Places address autocomplete on the claim address step's street field
// (2.9, sdkConfig.placesApiKey). Suggestions render INLINE under the field —
// inside the step's imePadding'd scrollable — so they stay visible and
// tappable with the keyboard open, and the field's BringIntoViewRequester
// scrolls the whole block (field + list) above the IME.
//

/** Debounce between the last keystroke and the autocomplete request. */
internal const val Avafli_PLACES_DEBOUNCE_MS = 300L

/** Minimum typed characters before suggestions are requested. */
internal const val Avafli_PLACES_MIN_CHARS = 3

/**
 * The street-address field. With a [placesClient] it augments the standard
 * claim-step field with a Places autocomplete list (typing debounced by
 * coroutine cancellation, up to 5 suggestions, tap to fill street/city/state/
 * zip via [onAddressResolved] — everything stays hand-editable). Without one
 * it IS the standard field: exactly the pre-2.9 behavior.
 *
 * Every Places failure degrades silently to plain typing — the list simply
 * doesn't appear; entry is never blocked.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AvafliClaimStepStreetField(
    label: String,
    value: String,
    placesClient: AvafliPlacesClient?,
    onValueChange: (String) -> Unit,
    onAddressResolved: (AvafliPlaceAddress) -> Unit,
) {
    if (placesClient == null) {
        AvafliClaimStepField(label, value, onValueChange = onValueChange)
        return
    }

    var suggestions by remember { mutableStateOf(emptyList<AvafliPlaceSuggestion>()) }
    var focused by remember { mutableStateOf(false) }
    // A tapped suggestion writes the field programmatically — don't re-query
    // (and reopen the list) until the person actually types again.
    var suppressQuery by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bringIntoView = remember { BringIntoViewRequester() }

    // Debounce by coroutine cancellation: every keystroke restarts this
    // effect, so only a ~300ms pause lets a request through — and a stale
    // in-flight request is cancelled rather than raced.
    LaunchedEffect(value, focused, suppressQuery) {
        if (!focused || suppressQuery || value.trim().length < Avafli_PLACES_MIN_CHARS) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(Avafli_PLACES_DEBOUNCE_MS)
        suggestions = placesClient.autocomplete(value.trim())
    }

    // Keep the list on-screen when it appears under an IME-hugging field.
    LaunchedEffect(suggestions.isNotEmpty()) {
        if (suggestions.isNotEmpty()) bringIntoView.bringIntoView()
    }

    // System back dismisses the list first (the keyboard's own back/down is
    // handled by the IME before it reaches us).
    BackHandler(enabled = suggestions.isNotEmpty()) { suggestions = emptyList() }

    with(AvafliClaimStepTheme) {
        Column(modifier = Modifier.bringIntoViewRequester(bringIntoView)) {
            AvafliClaimStepFieldLabel(label)
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(59.dp)
                    .fieldBackground()
                    .padding(horizontal = 25.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = {
                        suppressQuery = false
                        onValueChange(it)
                    },
                    textStyle = AvafliV2Font.inter(20.sp, color = Color.White),
                    singleLine = true,
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusEvent { state ->
                            // Focus loss = the person tapped elsewhere →
                            // dismiss (the query effect clears the list).
                            focused = state.isFocused
                            if (state.isFocused) {
                                scope.launch {
                                    delay(Avafli_IME_SETTLE_MS)
                                    bringIntoView.bringIntoView()
                                }
                            }
                        },
                )
            }

            if (suggestions.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .fieldBackground()
                        .clip(RoundedCornerShape(10.dp)),
                ) {
                    suggestions.forEach { suggestion ->
                        Text(
                            suggestion.text,
                            style = AvafliV2Font.inter(15.sp, color = Color.White),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    suppressQuery = true
                                    suggestions = emptyList()
                                    // Instant feedback: the tapped line becomes
                                    // the street text while details resolve.
                                    onValueChange(suggestion.text)
                                    scope.launch {
                                        placesClient.resolveAddress(suggestion.placeId)?.let { address ->
                                            onAddressResolved(
                                                // A detail payload without a
                                                // street keeps the tapped line.
                                                if (address.street.isBlank()) {
                                                    address.copy(street = suggestion.text)
                                                } else {
                                                    address
                                                }
                                            )
                                        }
                                    }
                                }
                                .padding(horizontal = 25.dp, vertical = 13.dp),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(fieldBorder.copy(alpha = 0.6f))
                        )
                    }
                    // Required Places attribution when suggestions show
                    // without a Google map.
                    Text(
                        "powered by Google",
                        style = AvafliV2Font.inter(11.sp, color = Color.White.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(horizontal = 25.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
