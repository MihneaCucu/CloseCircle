package com.closecircle.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecircle.app.data.Group
import com.closecircle.app.data.Member
import com.closecircle.app.domain.Freshness
import com.closecircle.app.domain.freshnessOf
import com.closecircle.app.domain.leftText
import com.closecircle.app.ui.AppViewModel
import com.closecircle.app.ui.Dot
import com.closecircle.app.ui.FreshChip
import com.closecircle.app.ui.GearButton
import com.closecircle.app.ui.MapCanvas
import com.closecircle.app.ui.MemberAvatar
import com.closecircle.app.ui.PillButton
import com.closecircle.app.ui.UiState
import com.closecircle.app.ui.theme.Cc

@Composable
fun MapScreen(ui: UiState, vm: AppViewModel, onManage: () -> Unit, onSettings: () -> Unit) {
    val c = Cc.colors
    val elder = Cc.elder
    val group = ui.group
    if (group == null) {
        NoGroup(ui.loaded, vm)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        val mapHeight = maxHeight * 0.50f
        Box(Modifier.fillMaxWidth().height(mapHeight)) {
            MapCanvas()
            BoxWithConstraints(Modifier.fillMaxSize()) {
                ui.members.filter { it.x > 0 }.forEach { m -> Pin(m, maxWidth * m.x / 100 - 22.dp, maxHeight * m.y / 100 - 22.dp) }
            }
            Column(Modifier.statusBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GroupPill(ui, vm, Modifier.weight(1f))
                    GearButton(onSettings)
                }
                Spacer(Modifier.height(10.dp))
                Banner(group, ui.now, onManage)
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.56f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = c.surface,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Membri", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
                    if (!elder) Text("${ui.members.size} din ${(group.members - 1).coerceAtLeast(0)} partajează", fontSize = 12.sp, color = c.muted)
                }
                if (ui.members.isEmpty()) {
                    Text("Niciun membru nu partajează cu tine în acest grup.", Modifier.padding(top = 16.dp), color = c.muted, fontSize = 14.sp)
                }
                LazyColumn { items(ui.members, key = { it.id }) { MemberRow(it) } }
            }
        }
    }
}

/** A new account has no group yet; the backend lets any user create one. */
@Composable
private fun NoGroup(loaded: Boolean, vm: AppViewModel) {
    val c = Cc.colors
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        if (!loaded) {
            Text("Se încarcă…", color = c.muted)
            return
        }
        Text("Nu ești încă într-un grup", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text("Creează un grup privat pentru familia ta. Invitațiile pentru ceilalți membri vin într-o etapă următoare.", Modifier.padding(top = 8.dp, bottom = 16.dp), color = c.muted, fontSize = 14.sp)
        OutlinedTextField(name, { name = it.take(100); error = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Numele grupului") }, isError = error != null)
        error?.let { Text(it, Modifier.padding(top = 4.dp), color = c.red, fontSize = 12.sp) }
        PillButton("Creează grupul", { error = vm.createGroup(name) }, Modifier.padding(top = 16.dp).fillMaxWidth())
    }
}

@Composable
private fun Pin(m: Member, x: Dp, y: Dp) {
    val c = Cc.colors
    Column(Modifier.offset(x, y), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, shadowElevation = 4.dp, color = c.surface) { MemberAvatar(m, 44.dp) }
        if (freshnessOf(m.minutesAgo) == Freshness.LAST_KNOWN) {
            Text(
                "acum ${m.minutesAgo} min",
                Modifier.padding(top = 4.dp).clip(CircleShape).background(c.surface).padding(horizontal = 8.dp, vertical = 2.dp),
                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.text,
            )
        }
    }
}

@Composable
private fun GroupPill(ui: UiState, vm: AppViewModel, modifier: Modifier) {
    val c = Cc.colors
    var open by remember { mutableStateOf(false) }
    val group = ui.group ?: return
    Box(modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).clickable { open = true },
            shape = CircleShape, color = c.surface, shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(start = 20.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Dot(if (group.on) c.accent else c.grey)
                Text(group.name, Modifier.weight(1f), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.KeyboardArrowDown, "Alege grupul", tint = c.text, modifier = Modifier.rotate(if (open) 180f else 0f))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = 280.dp), containerColor = c.surface, shape = RoundedCornerShape(24.dp)) {
            ui.groups.forEach { g ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(g.name, fontWeight = FontWeight.Bold, color = c.text)
                            Text("${g.members} membri · ${if (g.on) "partajezi" else "oprită"}", fontSize = 12.sp, color = c.muted)
                        }
                    },
                    leadingIcon = { Dot(if (g.on) c.accent else c.grey) },
                    trailingIcon = { if (g.id == group.id) Icon(Icons.Outlined.Check, null, tint = c.accentFg) },
                    onClick = { vm.selectGroup(g.id); open = false },
                )
            }
        }
    }
}

@Composable
private fun Banner(group: Group, now: Long, onManage: () -> Unit) {
    val c = Cc.colors
    val bg = if (group.on) c.accent else c.darkCard
    val fg = if (group.on) c.onAccent else c.onDark
    val text = when {
        !group.on -> "Nu partajezi cu acest grup"
        group.endsAt != null -> "Partajezi cu acest grup · încă ${leftText(group.endsAt - now)}"
        else -> "Partajezi cu acest grup · permanent"
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.LocationOn, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, Modifier.weight(1f), color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp)
        Text(
            if (group.on) "Gestionează" else "Activează",
            Modifier.clickable(onClick = onManage),
            color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun MemberRow(m: Member) {
    val c = Cc.colors
    val elder = Cc.elder
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MemberAvatar(m)
        Column(Modifier.weight(1f)) {
            Text(m.name, color = c.text, fontWeight = FontWeight.Bold)
            if (elder) FreshChip(m.minutesAgo, Modifier.padding(top = 4.dp)) else Text(m.accuracy, color = c.muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (!elder) FreshChip(m.minutesAgo, Modifier.widthIn(max = 150.dp))
    }
}
