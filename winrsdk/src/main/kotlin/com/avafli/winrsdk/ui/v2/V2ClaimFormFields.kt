package com.avafli.winrsdk.ui.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.R
import com.avafli.winrsdk.domain.PrizeClaimForm
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale

// The claim form's inputs, ported from iOS WINRClaimField + statePicker +
// photoSection: labeled dark rounded fields, a US-state dropdown, and the
// attach-a-photo affordance.

/** A labeled dark rounded text field (claim form). */
@Composable
internal fun WINRClaimField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    disabled: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column {
        WINRAutoSizeText(
            label,
            style = WINRV2Font.inter(12.sp, color = Color.White),
            minScale = 0.8f,
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(50.dp)
                .winrClaimFieldBackground()
                .alpha(if (disabled) 0.6f else 1f)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (disabled) {
                Text(value, style = WINRV2Font.inter(16.sp, color = Color.White.copy(alpha = 0.4f)))
            } else {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = WINRV2Font.inter(16.sp, color = Color.White),
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

/** US-state dropdown (claim form). */
@Composable
internal fun WINRClaimStatePicker(state: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("State", style = WINRV2Font.inter(12.sp, color = Color.White))
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(50.dp)
                .winrClaimFieldBackground()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WINRAutoSizeText(
                    if (state.isEmpty()) "Select" else state,
                    style = WINRV2Font.inter(
                        16.sp,
                        color = if (state.isEmpty()) Color.White.copy(alpha = 0.4f) else Color.White,
                    ),
                    minScale = 0.7f,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                ChevronDown()
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                PrizeClaimForm.usStates.forEach { candidate ->
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

/** SF "chevron.down" equivalent, drawn at 13dp. */
@Composable
private fun ChevronDown() {
    Canvas(Modifier.size(13.dp)) {
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

/** "ATTACH A PHOTO" outline button / attached-photo row (claim form). */
@Composable
internal fun WINRClaimPhotoSection(
    photo: ImageBitmap?,
    onAttach: () -> Unit,
    onRemove: () -> Unit,
) {
    if (photo != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .winrClaimFieldBackground()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                bitmap = photo,
                contentDescription = null,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Text(
                "Photo attached",
                style = WINRV2Font.inter(15.sp, FontWeight.SemiBold, color = Color.White),
                modifier = Modifier.padding(start = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.winr_close_x),
                    contentDescription = "Remove photo",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAttach),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.winr_paperclip),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp),
            )
            Text(
                "ATTACH A PHOTO",
                style = WINRV2Font.inter(17.sp, FontWeight.ExtraBold, tracking = (-0.3).sp, color = Color.White),
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}
