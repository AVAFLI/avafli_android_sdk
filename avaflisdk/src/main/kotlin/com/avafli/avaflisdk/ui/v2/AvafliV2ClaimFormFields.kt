package com.avafli.avaflisdk.ui.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The stepped claim form's inputs, ported from iOS AvafliV2ClaimStepPage.swift:
// labeled text field, locked field (winning email / Country), and the State
// dropdown, all in the Figma claim-step styling.

/** Field styling from the claim-step frames: #212832 fill, #3D424B border, r10. */
internal object AvafliClaimStepTheme {
    val fieldFill = Color(0xFF212832)
    val fieldBorder = Color(0xFF3D424B)

    /** Inline-error red, matching the capture/code screens' error text. */
    val errorRed = Color(0xFFFF6B63)

    fun Modifier.fieldBackground(error: Boolean = false): Modifier = this
        .background(fieldFill, RoundedCornerShape(10.dp))
        .border(1.dp, if (error) errorRed else fieldBorder, RoundedCornerShape(10.dp))
}

/** The 12sp field label above the box. */
@Composable
internal fun AvafliClaimStepFieldLabel(text: String) {
    AvafliAutoSizeText(
        text,
        style = AvafliV2Font.inter(12.sp, color = Color.White),
        minScale = 0.8f,
        modifier = Modifier.padding(start = 8.dp),
    )
}

/**
 * A labeled claim-step text field per the frames: 12sp label, 59dp box,
 * #212832 fill / #3D424B 1dp border / r10, 20sp input text.
 *
 * [errorText], when non-null, turns the border error-red and renders the
 * message inline under the box (Master Field List "User Message (UI)").
 *
 * [imeAction] wires the keyboard's action key: Next moves focus to the next
 * field down (which itself scrolls clear of the IME via the shared
 * bring-into-view-on-focus path); Done clears focus and closes the keyboard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AvafliClaimStepField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    errorText: String? = null,
    onValueChange: (String) -> Unit,
) {
    // IME never blocks fields (2.9, hardened 3.1): when this field gains
    // focus, ask the enclosing scrollable (whose viewport imePadding shrinks
    // above the keyboard) to scroll it into the visible area above the IME.
    val bringIntoView = remember { BringIntoViewRequester() }
    val focusManager = LocalFocusManager.current
    with(AvafliClaimStepTheme) {
        Column(modifier = Modifier.bringIntoViewRequester(bringIntoView)) {
            AvafliClaimStepFieldLabel(label)
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(59.dp)
                    .fieldBackground(error = errorText != null)
                    .padding(horizontal = 25.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = AvafliV2Font.inter(20.sp, color = Color.White),
                    singleLine = true,
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        autoCorrectEnabled = false,
                        imeAction = imeAction,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        onDone = { focusManager.clearFocus() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .avafliBringIntoViewOnFocus(bringIntoView),
                )
            }
            if (errorText != null) {
                Text(
                    errorText,
                    style = AvafliV2Font.inter(13.sp, color = errorRed),
                    modifier = Modifier.padding(start = 8.dp, top = 5.dp),
                )
            }
        }
    }
}

/**
 * A locked (non-editable) field — the winning email and Country rows. Shows
 * dimmed text; [showsChevron] mimics the Country dropdown from the frame.
 */
@Composable
internal fun AvafliClaimStepLockedField(
    label: String,
    value: String,
    dimmed: Boolean = true,
    showsChevron: Boolean = false,
) {
    with(AvafliClaimStepTheme) {
        Column {
            AvafliClaimStepFieldLabel(label)
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(59.dp)
                    .fieldBackground()
                    .padding(horizontal = 25.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvafliAutoSizeText(
                    value,
                    style = AvafliV2Font.inter(
                        20.sp,
                        color = if (dimmed) Color.White.copy(alpha = 0.3f) else Color.White,
                    ),
                    minScale = 0.7f,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (showsChevron) {
                    Spacer(Modifier.weight(1f))
                    AvafliClaimChevronDown()
                }
            }
        }
    }
}

/** The State dropdown: same box styling, menu of the 50 states + DC, chevron down. */
@Composable
internal fun AvafliClaimStepMenuField(
    label: String,
    options: List<String>,
    selection: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    with(AvafliClaimStepTheme) {
        Column {
            AvafliClaimStepFieldLabel(label)
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(59.dp)
                    .fieldBackground()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 25.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvafliAutoSizeText(
                        if (selection.isEmpty()) "Select" else selection,
                        style = AvafliV2Font.inter(
                            20.sp,
                            color = if (selection.isEmpty()) Color.White.copy(alpha = 0.3f) else Color.White,
                        ),
                        minScale = 0.7f,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.weight(1f))
                    AvafliClaimChevronDown()
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate) },
                            onClick = {
                                onSelect(candidate)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/** SF "chevron.down" equivalent, drawn at 15dp. */
@Composable
internal fun AvafliClaimChevronDown() {
    Canvas(Modifier.size(15.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.14f, size.height * 0.35f)
            lineTo(size.width * 0.5f, size.height * 0.68f)
            lineTo(size.width * 0.86f, size.height * 0.35f)
        }
        drawPath(
            path,
            Color.White.copy(alpha = 0.7f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
