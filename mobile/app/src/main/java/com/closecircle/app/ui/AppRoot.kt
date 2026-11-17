package com.closecircle.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.closecircle.app.ui.screens.AlertsScreen
import com.closecircle.app.ui.screens.LoginScreen
import com.closecircle.app.ui.screens.MapScreen
import com.closecircle.app.ui.screens.SettingsScreen
import com.closecircle.app.ui.screens.ShareScreen
import com.closecircle.app.ui.screens.ZonesScreen
import com.closecircle.app.ui.theme.Cc

private enum class Dest(val route: String, val label: String) {
    MAP("map", "Hartă"), SHARE("share", "Partajare"), ZONES("zones", "Zone"), ALERTS("alerts", "Alerte")
}

private const val SETTINGS = "settings"

@Composable
fun AppRoot(vm: AppViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    when (ui.auth) {
        Auth.Checking -> Box(Modifier.fillMaxSize())
        is Auth.SignedOut -> LoginScreen(ui.auth, vm)
        Auth.SignedIn -> Main(ui, vm)
    }
}

@Composable
private fun Main(ui: UiState, vm: AppViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val c = Cc.colors

    LaunchedEffect(ui.toast) {
        ui.toast?.let {
            snackbar.showSnackbar(it)
            vm.consumeToast()
        }
    }

    fun go(r: String) = nav.navigate(r) {
        popUpTo(Dest.MAP.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = c.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = c.surface) {
                Dest.entries.forEach { d ->
                    val selected = route == d.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { go(d.route) },
                        icon = {
                            BadgedBox(badge = { if (d == Dest.ALERTS && ui.invites.isNotEmpty()) Badge(containerColor = c.red) }) {
                                when (d) {
                                    Dest.MAP -> Icon(Icons.Outlined.LocationOn, null)
                                    Dest.SHARE -> Icon(Icons.Outlined.Lock, null)
                                    Dest.ZONES -> ZonesTabIcon(if (selected) c.accentFg else c.muted)
                                    Dest.ALERTS -> Icon(Icons.Outlined.Notifications, null)
                                }
                            }
                        },
                        label = { Text(d.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.accentFg, selectedTextColor = c.accentFg,
                            unselectedIconColor = c.muted, unselectedTextColor = c.muted,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.padding(bottom = inner.calculateBottomPadding())) {
            val openSettings = { nav.navigate(SETTINGS) { launchSingleTop = true } }
            NavHost(nav, startDestination = Dest.MAP.route) {
                composable(Dest.MAP.route) { MapScreen(ui, vm, onManage = { go(Dest.SHARE.route) }, onSettings = openSettings) }
                composable(Dest.SHARE.route) { ShareScreen(ui, vm, openSettings) }
                composable(Dest.ZONES.route) { ZonesScreen(ui, vm, openSettings) }
                composable(Dest.ALERTS.route) { AlertsScreen(ui, vm, openSettings) }
                composable(SETTINGS) { SettingsScreen(ui, vm, onBack = { nav.popBackStack() }) }
            }
        }
    }
}
