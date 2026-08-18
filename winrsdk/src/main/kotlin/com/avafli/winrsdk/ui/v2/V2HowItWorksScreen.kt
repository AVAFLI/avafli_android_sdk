package com.avafli.winrsdk.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// How-it-works screen, ported from iOS WINRV2HowItWorksView. The header swaps
// the "?" for a back arrow that returns to the previous screen.
//
// 2.9.5: the "Privacy choices" fine print is GONE (it was redundant once the
// legal webview landed) — the delete path stays findable through the Privacy
// Policy webview reached from the legal-links rows (dashboard/code entry) and
// the capture screen's inline links: the ?app=1 build carries the
// delete-my-data section, whose winr://delete bridge raises the destructive
// confirmation at the V2 root (WINRV2OptOutConfirmDialog).

@Composable
internal fun WINRV2HowItWorksScreen(
    accent: Color,
    logoUrl: String?,
    day1Entries: Int,
    visitMode: Boolean = false,
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WINRV2Color.panel),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WINRV2Header(
            logoUrl = logoUrl,
            showsBack = true,
            onBack = onDone,
            onInfo = {},
            onClose = onClose,
            modifier = Modifier.padding(top = 18.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(39.dp)
                .background(Color.White.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "HOW IT WORKS",
                style = WINRV2Font.inter(26.sp, FontWeight.Black, tracking = (-0.78).sp, color = WINRV2Color.gunmetal),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HowItWorksItem(
                    number = "1",
                    title = "ENTER ONCE",
                    body = "Submit your email to receive $day1Entries entries instantly and start your streak.",
                )
                HowItWorksItem(
                    number = "2",
                    title = if (visitMode) "KEEP VISITING" else "VISIT EVERY DAY",
                    body = if (visitMode) {
                        "Simply open the app whenever you like. Your entries are added automatically—no forms or extra steps."
                    } else {
                        "Simply open the app each day. Your entries are added automatically—no forms or extra steps."
                    },
                )
                HowItWorksItem(
                    number = "3",
                    title = "KEEP YOUR STREAK GROWING",
                    body = if (visitMode) {
                        "Earn more entries with every visit. The more you come back, the bigger your rewards!"
                    } else {
                        "Earn more entries with every consecutive visit. The longer your streak, the bigger your daily rewards!"
                    },
                )
            }

            Text(
                if (visitMode) {
                    "Every visit counts - your streak never resets."
                } else {
                    "Don’t miss a day - your streak resets if you do."
                },
                style = WINRV2Font.inter(
                    20.sp, FontWeight.Bold,
                    tracking = (-0.6).sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .padding(horizontal = 40.dp)
                    .padding(top = 22.dp),
            )

            WINRV2PillButton(
                accent = accent,
                title = "GOT IT - START MY STREAK",
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .padding(top = 20.dp),
            ) { onDone() }

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun HowItWorksItem(number: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            "$number.",
            style = WINRV2Font.inter(18.sp, FontWeight.Black, color = Color.White),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = WINRV2Font.inter(18.sp, FontWeight.Black, color = Color.White),
            )
            Text(
                body,
                style = WINRV2Font.inter(16.sp, color = Color.White),
            )
        }
    }
}
