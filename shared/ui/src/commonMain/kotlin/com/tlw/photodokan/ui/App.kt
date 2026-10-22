package com.tlw.photodokan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.model.Order
import com.tlw.photodokan.core.model.SheetState
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.core.packing.PackingEngine
import org.koin.compose.koinInject

/** Root of the app on both platforms. For now it shows an example packed sheet. */
@Composable
fun App() {
    val template = koinInject<SheetTemplate>()
    val example = remember(template) {
        PackingEngine.pack(
            template,
            SheetState.FRESH,
            listOf(
                Order("0001", "Customer 0001", 5),
                Order("0002", "Customer 0002", 10),
                Order("0003", "Customer 0003", 4),
                Order("0004", "Customer 0004", 5),
            ),
        )
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Photo Dokan", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Example: ${example.copiesPlaced} photos for 4 customers on one ${template.name} sheet",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SheetPreview(
                    template = template,
                    sheet = example.sheets.first(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
