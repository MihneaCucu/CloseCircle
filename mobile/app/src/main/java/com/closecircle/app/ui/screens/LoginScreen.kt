package com.closecircle.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecircle.app.ui.Auth
import com.closecircle.app.ui.AppViewModel
import com.closecircle.app.ui.MIN_PASSWORD_CHARS
import com.closecircle.app.ui.PillButton
import com.closecircle.app.ui.theme.Cc

@Composable
fun LoginScreen(auth: Auth, vm: AppViewModel) {
    val c = Cc.colors
    var creating by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val state = auth as? Auth.SignedOut
    val busy = state?.busy == true

    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("CloseCircle", color = c.accentFg, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Text(if (creating) "Creează cont" else "Autentificare", Modifier.padding(top = 4.dp, bottom = 24.dp), color = c.text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        if (creating) {
            OutlinedTextField(name, { name = it.take(100) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nume afișat") }, enabled = !busy)
        }
        OutlinedTextField(
            email, { email = it }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true, label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !busy,
        )
        OutlinedTextField(
            password, { password = it }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true, label = { Text("Parolă") },
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !busy,
            supportingText = if (creating) ({ Text("Cel puțin $MIN_PASSWORD_CHARS caractere") }) else null,
        )
        state?.error?.let { Text(it, Modifier.padding(top = 12.dp), color = c.red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        PillButton(
            if (busy) "Se încarcă…" else if (creating) "Creează contul" else "Intră în cont",
            { if (creating) vm.register(email, password, name) else vm.signIn(email, password) },
            Modifier.fillMaxWidth().padding(top = 20.dp), enabled = !busy,
        )
        TextButton({ creating = !creating }, Modifier.padding(top = 8.dp), enabled = !busy) {
            Text(if (creating) "Ai deja cont? Autentifică-te" else "Nu ai cont? Creează unul", color = c.accentFg, fontWeight = FontWeight.Bold)
        }
    }
}
