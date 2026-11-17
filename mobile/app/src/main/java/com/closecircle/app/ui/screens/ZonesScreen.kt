package com.closecircle.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecircle.app.data.Zone
import com.closecircle.app.ui.AppViewModel
import com.closecircle.app.ui.Cap
import com.closecircle.app.ui.CcCard
import com.closecircle.app.ui.MapCanvas
import com.closecircle.app.ui.PillButton
import com.closecircle.app.ui.UiState
import com.closecircle.app.ui.ZoneCircle
import com.closecircle.app.ui.outlined
import com.closecircle.app.ui.theme.Cc

// Zones are not served by the backend yet; this screen shows the list the app keeps locally (see PendingBackendData).
@Composable
fun ZonesScreen(ui: UiState, vm: AppViewModel) {
    val c = Cc.colors
    var open by remember { mutableStateOf<String?>("acasa") }
    LazyColumn(Modifier.fillMaxSize().background(c.bg), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.statusBarsPadding().fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Zone", color = c.text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
                    Text("${ui.group?.name ?: "Grupul tău"} · ${ui.zones.size} zone", color = c.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                }
                PillButton("+ Zonă nouă", { vm.showMessage("Zonă nouă — disponibil în curând") }, Modifier.widthIn(min = 0.dp))
            }
            Spacer(Modifier.height(20.dp))
        }
        items(ui.zones, key = { it.id }) { z ->
            ZoneCard(z, open == z.id, ui, vm, onToggle = { open = if (open == z.id) null else z.id })
            Spacer(Modifier.height(12.dp))
        }
        item {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Info, null, tint = c.muted, modifier = Modifier.size(18.dp))
                Text("Sosirea sau plecarea se confirmă după 2 minute în noua zonă. Locațiile cu precizie peste 500 m nu declanșează alerte.", color = c.muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun ZoneCard(z: Zone, open: Boolean, ui: UiState, vm: AppViewModel, onToggle: () -> Unit) {
    val c = Cc.colors
    CcCard(Modifier.fillMaxWidth()) {
        Column {
            AnimatedVisibility(open) {
                Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                    MapCanvas()
                    ZoneCircle(84.dp, center = true)
                }
            }
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!open) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (z.home) c.accentSoft else c.amberBg),
                        contentAlignment = Alignment.Center,
                    ) { Icon(if (z.home) Icons.Outlined.Home else Icons.Outlined.LocationOn, null, tint = if (z.home) c.accentFg else c.amberFg) }
                }
                Column(Modifier.weight(1f)) {
                    Text(z.name, color = c.text, fontWeight = FontWeight.Bold, fontSize = if (open) 18.sp else 16.sp)
                    if (!open) Text(z.sub, color = c.muted, fontSize = 12.sp)
                }
                if (open) Text(z.sub.substringBefore(" · "), color = c.muted, fontSize = 14.sp)
                else Icon(Icons.Outlined.KeyboardArrowRight, null, tint = c.muted)
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    Cap("MĂ ANUNȚĂ CÂND")
                    Spacer(Modifier.height(6.dp))
                    listOf("ana" to "Ana", "mihai" to "Mihai").forEach { (id, name) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, color = c.text, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("in" to "Sosește", "out" to "Pleacă").forEach { (k, label) ->
                                    val key = "${z.id}-$id-$k"
                                    val on = key in ui.zoneNotify
                                    Text(
                                        label,
                                        Modifier.widthIn(min = 84.dp).outlined(on).clickable { vm.toggleZoneNotify(key) }.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = if (on) c.accentFg else c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
