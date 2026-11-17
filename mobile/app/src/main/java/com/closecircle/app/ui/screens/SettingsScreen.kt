package com.closecircle.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecircle.app.ui.AppViewModel
import com.closecircle.app.ui.Cap
import com.closecircle.app.ui.CcCard
import com.closecircle.app.ui.CcSwitch
import com.closecircle.app.ui.UiState
import com.closecircle.app.ui.theme.Cc

@Composable
fun SettingsScreen(ui: UiState, vm: AppViewModel, onBack: () -> Unit) {
    val c = Cc.colors
    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
        Row(
            Modifier.clip(CircleShape).background(c.surface).clickable(onClick = onBack).padding(start = 8.dp, end = 16.dp).height(44.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = c.text, modifier = Modifier.size(20.dp))
            Text("Înapoi", color = c.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Text("Setări", Modifier.padding(top = 16.dp), color = c.text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)

        Cap("ACCESIBILITATE", Modifier.padding(top = 24.dp, bottom = 8.dp))
        CcCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Mod pentru persoane în vârstă", color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Text mare, contrast ridicat, butoane mari și doar acțiunile esențiale.", color = c.muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
                CcSwitch(ui.elder, { vm.setElder(it) }, "Mod pentru persoane în vârstă")
            }
        }

        Cap("CONT", Modifier.padding(top = 24.dp, bottom = 8.dp))
        CcCard(Modifier.fillMaxWidth()) {
            Text(
                "Deconectare",
                Modifier.fillMaxWidth().clickable { vm.signOut() }.padding(16.dp),
                color = c.text, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            )
        }
        Text("Preferința pentru modul persoane în vârstă se păstrează și după ce închizi aplicația.", Modifier.padding(top = 16.dp), color = c.muted, fontSize = 12.sp)
    }
}
