package com.tlw.photodokan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.model.Order
import com.tlw.photodokan.core.model.SheetState
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.core.packing.PackingEngine
import com.tlw.photodokan.ui.SheetPreview
import org.koin.compose.koinInject

@Composable
private fun Screen(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        content()
    }
}

@Composable
fun CustomersScreen(modifier: Modifier = Modifier) = Screen("Customers", modifier) {
    Text("Today's customers will appear here.", style = MaterialTheme.typography.bodyLarge)
}

/** For now shows an example packed sheet; the order queue comes with the sheet screen task. */
@Composable
fun PrintScreen(modifier: Modifier = Modifier) = Screen("Print", modifier) {
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
    Text(
        "Example: ${example.copiesPlaced} photos for 4 customers on one ${template.name} sheet",
        style = MaterialTheme.typography.bodyMedium,
    )
    SheetPreview(template = template, sheet = example.sheets.first(), modifier = Modifier.weight(1f))
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) = Screen("Settings", modifier) {
    Text("Printer, photo sizes and the photo library folder will be set here.", style = MaterialTheme.typography.bodyLarge)
}
