package com.tlw.photodokan.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/** Whether a screen has asked for the whole phone screen; the app hides its bottom bar meanwhile. */
val LocalFullScreen = staticCompositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }

/** Asks for the whole phone screen while this is shown, e.g. for the photo editor. */
@Composable
fun FullScreen() {
    val fullScreen = LocalFullScreen.current
    DisposableEffect(fullScreen) {
        fullScreen.value = true
        onDispose { fullScreen.value = false }
    }
}
