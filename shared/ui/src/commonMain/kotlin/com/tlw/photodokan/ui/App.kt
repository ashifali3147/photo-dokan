package com.tlw.photodokan.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.tlw.photodokan.ui.navigation.Destination
import com.tlw.photodokan.ui.photo.IncomingPhotos
import com.tlw.photodokan.ui.customers.CustomersScreen
import com.tlw.photodokan.ui.platform.SystemBackHandler
import com.tlw.photodokan.ui.print.PrintScreen
import com.tlw.photodokan.ui.screens.SettingsScreen
import com.tlw.photodokan.ui.station.IncomingInvites
import com.tlw.photodokan.ui.theme.PhotoDokanTheme
import com.tlw.photodokan.ui.theme.ThemeSettings
import org.koin.compose.koinInject

/** Root of the app on both platforms: a side rail on wide windows, a bottom bar on phones. */
@Composable
fun App() {
    // Screens keep their ViewModels across tabs. Android's activity provides the store; where the
    // host doesn't (desktop window, UI tests), the app keeps its own for as long as it is shown.
    val owner = LocalViewModelStoreOwner.current ?: remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }.also { DisposableEffect(it) { onDispose { it.viewModelStore.clear() } } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { AppContent() }
}

@Composable
private fun AppContent() {
    var current by rememberSaveable { mutableStateOf(Destination.Customers) }

    // A photo shared from another app opens on the Customers screen, which imports it.
    val incoming by koinInject<IncomingPhotos>().pending.collectAsState()
    LaunchedEffect(incoming) {
        if (incoming != null) current = Destination.Customers
    }
    // A pairing link opened from the phone's camera app pairs on the Settings screen.
    val invite by koinInject<IncomingInvites>().pending.collectAsState()
    LaunchedEffect(invite) {
        if (invite != null) current = Destination.Settings
    }

    // Back from Print or Settings returns to Customers; Back there leaves the app.
    SystemBackHandler(enabled = current != Destination.Customers) { current = Destination.Customers }

    val theme by koinInject<ThemeSettings>().mode.collectAsState()
    PhotoDokanTheme(theme) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            BoxWithConstraints {
                if (maxWidth >= 600.dp) {
                    Row {
                        NavigationRail {
                            for (destination in Destination.entries) {
                                NavigationRailItem(
                                    selected = destination == current,
                                    onClick = { current = destination },
                                    icon = { Icon(destination.icon, contentDescription = null) },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                        DestinationScreen(current, Modifier.weight(1f))
                    }
                } else {
                    Scaffold(
                        contentWindowInsets = WindowInsets(0),
                        bottomBar = {
                            NavigationBar(windowInsets = WindowInsets(0)) {
                                for (destination in Destination.entries) {
                                    NavigationBarItem(
                                        selected = destination == current,
                                        onClick = { current = destination },
                                        icon = { Icon(destination.icon, contentDescription = null) },
                                        label = { Text(destination.label) },
                                    )
                                }
                            }
                        },
                    ) { padding ->
                        DestinationScreen(current, Modifier.padding(padding))
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationScreen(destination: Destination, modifier: Modifier) = when (destination) {
    Destination.Customers -> CustomersScreen(modifier)
    Destination.Print -> PrintScreen(modifier)
    Destination.Settings -> SettingsScreen(modifier)
}
