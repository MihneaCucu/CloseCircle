package com.closecircle.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecircle.app.data.DurId
import com.closecircle.app.data.Group
import com.closecircle.app.domain.leftText
import com.closecircle.app.domain.lengthText
import com.closecircle.app.domain.whenText
import com.closecircle.app.ui.AppViewModel
import com.closecircle.app.ui.Cap
import com.closecircle.app.ui.CcCard
import com.closecircle.app.ui.CcSwitch
import com.closecircle.app.ui.GearButton
import com.closecircle.app.ui.MIN_CUSTOM_MIN
import com.closecircle.app.ui.PillButton
import com.closecircle.app.ui.UiState
import com.closecircle.app.ui.outlined
import com.closecircle.app.ui.theme.Cc

private val QUICK = listOf(DurId.H1 to "1 oră", DurId.H4 to "4 ore", DurId.H8 to "8 ore", DurId.ALWAYS to "Permanent")
private val PRESETS = listOf(30 to "30 min", 120 to "2 h", 720 to "12 h", 1440 to "24 h", 4320 to "3 zile")
private const val MAX_CUSTOM_MIN = 7 * 24 * 60

@Composable
fun ShareScreen(ui: UiState, vm: AppViewModel, onSettings: () -> Unit) {
    val c = Cc.colors
    val elder = Cc.elder
    var confirmStop by remember { mutableStateOf(false) }
    var customFor by remember { mutableStateOf<Group?>(null) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp)) {
            item {
                Column(Modifier.statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Partajarea mea", Modifier.weight(1f), color = c.text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
                        GearButton(onSettings)
                    }
                    if (!elder) Text("Tu decizi cine te vede, cât timp, separat pentru fiecare grup.", Modifier.padding(top = 6.dp, bottom = 4.dp), color = c.muted, fontSize = 15.sp)
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (ui.loaded && ui.groups.isEmpty()) {
                item { Text("Nu ești într-un grup. Creează unul din ecranul Hartă.", color = c.muted, fontSize = 14.sp) }
            }
            items(ui.groups, key = { it.id }) { g ->
                GroupCard(g, ui.now, elder, onToggle = { vm.toggleGroup(g) }, onQuick = { id, label -> vm.pickDuration(g, id, label) }, onCustom = { customFor = g })
                Spacer(Modifier.height(12.dp))
            }
            if (!elder) item {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = c.muted, modifier = Modifier.size(18.dp))
                    Text(
                        "Când oprești partajarea, membrii nu mai văd nici poziția curentă, nici ultima locație cunoscută, iar alertele în așteptare se anulează.",
                        color = c.muted, fontSize = 13.sp, lineHeight = 19.sp,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            PillButton(
                "Oprește partajarea în toate grupurile", onClick = { confirmStop = true }, modifier = Modifier.fillMaxWidth(),
                container = c.red, content = c.onRed, enabled = ui.groups.any { it.on },
            )
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            containerColor = c.surface,
            title = { Text("Oprești partajarea peste tot?", color = c.text, fontWeight = FontWeight.ExtraBold) },
            text = { Text("Nimeni nu îți va mai vedea locația în niciun grup, iar alertele în așteptare se anulează.", color = c.muted) },
            confirmButton = { TextButton({ confirmStop = false; vm.stopAll() }) { Text("Oprește", color = c.red, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ confirmStop = false }) { Text("Anulează", color = c.text) } },
        )
    }
    customFor?.let { g ->
        CustomDurationDialog(g, ui.now, onDismiss = { customFor = null }, onApply = { min -> if (vm.applyCustom(g, min)) customFor = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupCard(g: Group, now: Long, elder: Boolean, onToggle: () -> Unit, onQuick: (DurId, String) -> Unit, onCustom: () -> Unit) {
    val c = Cc.colors
    CcCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(g.name, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (g.on) "Activă · ${if (g.endsAt != null) "se oprește ${whenText(g.endsAt, now)}" else "permanent"}" else "Oprită · nimeni nu te vede",
                        color = if (g.on) c.accentFg else c.muted, fontSize = 13.sp, fontWeight = if (g.on) FontWeight.Bold else FontWeight.Medium,
                    )
                }
                CcSwitch(g.on, { onToggle() }, "Partajare ${g.name}")
            }
            if (g.on && !elder) {
                Spacer(Modifier.height(16.dp))
                Cap("DURATĂ")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK.forEach { (id, label) -> DurationChip(label, g.durId == id) { onQuick(id, label) } }
                    DurationChip(if (g.durId == DurId.CUSTOM && g.customLabel.isNotEmpty()) g.customLabel else "Personalizat", g.durId == DurId.CUSTOM, onClick = onCustom)
                }
                if (g.endsAt != null) {
                    val pct = ((g.endsAt - now).toFloat() / (g.endsAt - g.startedAt).coerceAtLeast(1)).coerceIn(0.02f, 1f)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { pct }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = c.accent, trackColor = c.soft2, strokeCap = StrokeCap.Round)
                }
                Text(
                    listOfNotNull(g.endsAt?.let { "Încă ${leftText(it - now)}" } ?: "Fără oprire automată", "${(g.members - 1).coerceAtLeast(0)} membri te pot vedea").joinToString(" · "),
                    Modifier.padding(top = 8.dp), color = c.muted, fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun DurationChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Cc.colors
    Text(
        label,
        Modifier.outlined(selected).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        color = if (selected) c.accentFg else c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomDurationDialog(g: Group, now: Long, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    val c = Cc.colors
    var hours by remember { mutableIntStateOf(2) }
    var mins by remember { mutableIntStateOf(0) }
    val total = hours * 60 + mins
    val valid = total in MIN_CUSTOM_MIN..MAX_CUSTOM_MIN
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("Durată personalizată", color = c.text, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text("${g.name} · partajarea se oprește automat.", color = c.muted, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stepper("ORE", hours, { hours = (hours + it).coerceIn(0, 168) }, 1)
                    Stepper("MINUTE", mins, { mins = (mins + it + 60) % 60 }, 5)
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { (m, label) -> Text(label, Modifier.outlined(false).clickable { hours = m / 60; mins = m % 60 }.padding(horizontal = 12.dp, vertical = 6.dp), color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (valid) "Se oprește ${whenText(now + total * 60_000L, now)} · peste ${lengthText(total)}" else "Alege între 5 minute și 7 zile.",
                    color = if (valid) c.text else c.red, fontSize = 14.sp,
                )
            }
        },
        confirmButton = { TextButton({ onApply(total) }, enabled = valid) { Text("Aplică", fontWeight = FontWeight.Bold, color = if (valid) c.accentFg else c.muted) } },
        dismissButton = { TextButton(onDismiss) { Text("Anulează", color = c.text) } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, onStep: (Int) -> Unit, step: Int) {
    val c = Cc.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Cap(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onStep(-step) }) { Text("−", color = c.text, fontSize = 24.sp) }
            Text(value.toString().padStart(2, '0'), color = c.text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            IconButton({ onStep(step) }) { Text("+", color = c.text, fontSize = 24.sp) }
        }
    }
}
