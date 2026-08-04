package com.avafli.winrsdk.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.R
import kotlinx.coroutines.delay

// STREAK STEP rail (streak tiles + accelerator power-ups), ported from iOS
// WINRV2StreakRail. The "DAILY PROGRESS ▾" pointer rides ABOVE the current tile
// INSIDE the horizontally-scrolling rail (it scrolls with it), and the rail
// auto-centers the active tile after ~350ms.

internal sealed class WINRV2RailEntry(val id: String) {
    class Day(
        id: String,
        val day: Int,
        val entries: Int,
        val state: WINRV2TileState,
    ) : WINRV2RailEntry(id)

    class PowerUp(
        id: String,
        val label: String,
        val bonus: Int,
        val footnote: String,
    ) : WINRV2RailEntry(id)
}

@Composable
internal fun WINRV2StreakRail(
    accent: Color,
    entries: List<WINRV2RailEntry>,
    activeId: String?,
    visitMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    LaunchedEffect(activeId) {
        val index = entries.indexOfFirst { it.id == activeId }
        if (index < 0) return@LaunchedEffect
        delay(350)
        val viewport = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        val itemWidth = with(density) { 106.dp.roundToPx() }
        listState.animateScrollToItem(index, scrollOffset = -((viewport - itemWidth) / 2))
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        items(entries.size, key = { entries[it].id }) { i ->
            val entry = entries[i]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProgressPointer(visible = entry.id == activeId, visitMode = visitMode)
                when (entry) {
                    is WINRV2RailEntry.Day -> WINRV2StreakTile(
                        accent = accent,
                        day = entry.day,
                        entries = entry.entries,
                        state = entry.state,
                        visitMode = visitMode,
                    )
                    is WINRV2RailEntry.PowerUp -> WINRV2PowerUpTile(
                        accent = accent,
                        label = entry.label,
                        bonus = entry.bonus,
                        footnote = entry.footnote,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressPointer(visible: Boolean, visitMode: Boolean) {
    Column(
        modifier = Modifier
            .alpha(if (visible) 1f else 0f)
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (visitMode) "PROGRESS" else "DAILY PROGRESS",
            style = WINRV2Font.oswald(12.sp, color = Color.White),
            maxLines = 1,
        )
        Icon(
            painter = painterResource(R.drawable.winr_arrow_drop_down),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(width = 10.dp, height = 6.dp),
        )
    }
}
