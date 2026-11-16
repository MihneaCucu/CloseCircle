package com.closecircle.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecircle.app.data.AlertItem
import com.closecircle.app.data.AlertKind
import com.closecircle.app.domain.isDelayed
import com.closecircle.app.ui.AppViewModel
import com.closecircle.app.ui.Cap
import com.closecircle.app.ui.CcCard
import com.closecircle.app.ui.GearButton
import com.closecircle.app.ui.PillButton
import com.closecircle.app.ui.UiState
import com.closecircle.app.ui.theme.Cc

// Invitations and zone alerts are not served by the backend yet; the lists come from PendingBackendData.
@Composable
fun AlertsScreen(ui: UiState, vm: AppViewModel, onSettings: () -> Unit) {
    val c = Cc.colors
    LazyColumn(Modifier.fillMaxSize().background(c.bg), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.statusBarsPadding().fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Alerte", color = c.text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
                GearButton(onSettings)
            }
        }
        items(ui.invites, key = { it.id }) { inv ->
            Column(
                Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.darkCard).padding(16.dp),
            ) {
                Text("INVITAȚIE NOUĂ", color = c.onDarkAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Text("${inv.by} te-a invitat în grupul „${inv.group}”", Modifier.padding(top = 6.dp), color = c.onDark, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
                Text("Expiră în ${inv.expiresInHours} h. Partajarea ta va fi oprită până o activezi.", Modifier.padding(top = 4.dp), color = c.onDarkMuted, fontSize = 13.sp, lineHeight = 18.sp)
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Acceptă", { vm.answerInvite(inv.id, true) }, Modifier.weight(1f), container = Color.White, content = Color(0xFF16211F))
                    PillButton("Refuză", { vm.answerInvite(inv.id, false) }, Modifier.weight(1f), container = Color.Transparent, content = c.onDark, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)))
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        items(ui.alerts, key = { it.id }) { a ->
            if (a.id == ui.alerts.first().id) Cap("AZI", Modifier.padding(top = 12.dp, bottom = 8.dp))
            if (a.id == "a4") Cap("IERI", Modifier.padding(top = 16.dp, bottom = 8.dp))
            AlertRow(a)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AlertRow(a: AlertItem) {
    val c = Cc.colors
    val (bg, fg, icon) = when (a.kind) {
        AlertKind.ARRIVE -> Triple(c.accentSoft, c.accentFg, Icons.AutoMirrored.Outlined.ArrowForward)
        AlertKind.LEAVE -> Triple(c.amberBg, c.amberFg, Icons.AutoMirrored.Outlined.ArrowBack)
        AlertKind.STOP -> Triple(c.soft, c.muted, Icons.Outlined.Lock)
        AlertKind.EXPIRED -> Triple(c.soft, c.muted, Icons.Outlined.Notifications)
    }
    CcCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                Icon(icon as ImageVector, null, tint = fg, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(a.who) }
                        append(a.verb)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(a.what) }
                        append(a.tail)
                    },
                    color = c.text, fontSize = 15.sp, lineHeight = 20.sp,
                )
                Text(a.meta, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                if (isDelayed(a.delayMin)) {
                    Text(
                        "ÎNTÂRZIAT ${a.delayMin} MIN",
                        Modifier.padding(top = 6.dp).clip(CircleShape).background(c.amberBg).padding(horizontal = 10.dp, vertical = 4.dp),
                        color = c.amberFg, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}
