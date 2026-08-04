package com.avafli.winrsdk.ui.v2

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

// The stepped claim form's inputs, ported from iOS WINRV2ClaimStepPage.swift:
// labeled text field, locked field (winning email / Country), and the State
// dropdown, all in the Figma claim-step styling.

/** Field styling from the claim-step frames: #212832 fill, #3D424B border, r10. */
internal object WINRClaimStepTheme {
    val fieldFill = Color(0xFF212832)
    val fieldBorder = Color(0xFF3D424B)

    fun Modifier.fieldBackground(): Modifier = this
        .background(fieldFill, RoundedCornerShape(10.dp))
        .border(1.dp, fieldBorder, RoundedCornerShape(10.dp))
}

/** The 12sp field label above the box. */
@Composable
internal fun WINRClaimStepFieldLabel(text: String) {
    WINRAutoSizeText(
        text,
        style = WINRV2Font.inter(12.sp, color = Color.White),
        minScale = 0.8f,
        modifier = Modifier.padding(start = 8.dp),
    )
}

/**
 * A labeled claim-step text field per the frames: 12sp label, 59dp box,
 * #212832 fill / #3D424B 1dp border / r10, 20sp input text.
 */
@Composable
internal fun WINRClaimStepField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    with(WINRClaimStepTheme) {
        Column {
            WINRClaimStepFieldLabel(label)
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
                    onValueChange = onValueChange,
                    textStyle = WINRV2Font.inter(20.sp, color = Color.White),
                    singleLine = true,
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
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
internal fun WINRClaimStepLockedField(
    label: String,
    value: String,
    dimmed: Boolean = true,
    showsChevron: Boolean = false,
) {
    with(WINRClaimStepTheme) {
        Column {
            WINRClaimStepFieldLabel(label)
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(59.dp)
                    .fieldBackground()
                    .padding(horizontal = 25.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WINRAutoSizeText(
                    value,
                    style = WINRV2Font.inter(
                        20.sp,
                        color = if (dimmed) Color.White.copy(alpha = 0.3f) else Color.White,
                    ),
                    minScale = 0.7f,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (showsChevron) {
                    Spacer(Modifier.weight(1f))
                    WINRClaimChevronDown()
                }
            }
        }
    }
}

/** The State dropdown: same box styling, menu of the 50 states, chevron down. */
@Composable
internal fun WINRClaimStepMenuField(
    label: String,
    options: List<String>,
    selection: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    with(WINRClaimStepTheme) {
        Column {
            WINRClaimStepFieldLabel(label)
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
                    WINRAutoSizeText(
                        if (selection.isEmpty()) "Select" else selection,
                        style = WINRV2Font.inter(
                            20.sp,
                            color = if (selection.isEmpty()) Color.White.copy(alpha = 0.3f) else Color.White,
                        ),
                        minScale = 0.7f,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.weight(1f))
                    WINRClaimChevronDown()
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
internal fun WINRClaimChevronDown() {
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
