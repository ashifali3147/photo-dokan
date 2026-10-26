package com.tlw.photodokan.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject

/** Dark, light, or whatever the device uses (Auto), saved on this device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(settings: ThemeSettings = koinInject()) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    val mode by settings.mode.collectAsState()
    Text("Appearance", style = MaterialTheme.typography.titleLarge)
    Text(
        "Dark is easier on the eyes in a dim studio, light suits a bright shop counter. Auto follows the device.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val options = ThemeMode.entries
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == mode,
                onClick = { settings.choose(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) {
                Text(option.label)
            }
        }
    }
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.System -> "Auto"
        ThemeMode.Dark -> "Dark"
        ThemeMode.Light -> "Light"
    }
