package com.tlw.photodokan.ui.customers

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.ui.components.Card
import com.tlw.photodokan.ui.components.CustomerNumber
import com.tlw.photodokan.ui.components.QuietButton
import com.tlw.photodokan.ui.components.pressScale
import com.tlw.photodokan.ui.components.riseIn
import com.tlw.photodokan.ui.components.selectionGlow
import com.tlw.photodokan.ui.theme.StudioTheme

/**
 * The day's customers as a contact sheet: find them by photo, with the number big underneath.
 * [onImport] is the PC's quiet "Import photo" button; on a phone the screen puts Take photo at
 * the bottom instead, so it is null there.
 */
@Composable
internal fun CustomerGrid(
    state: CustomersState,
    thumbnails: Map<Customer, ImageBitmap>,
    onLoadThumbnail: (Customer) -> Unit,
    onSelect: (Customer) -> Unit,
    onSearch: (String) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    isQueued: (Customer) -> Boolean,
    columns: GridCells,
    modifier: Modifier = Modifier,
    onImport: (() -> Unit)? = null,
) {
    val c = StudioTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.date == state.today) "Today" else state.date.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = c.text,
                )
                Text(subtitle(state), style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
            }
            if (!state.searching) {
                QuietButton(onPreviousDay) { Text("Earlier") }
                if (state.date < state.today) QuietButton(onNextDay) { Text("Later") }
                if (state.date != state.today) QuietButton(onToday) { Text("Today") }
            }
            onImport?.let { QuietButton(it, enabled = !state.busy) { Text("Import photo") } }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onSearch,
            placeholder = { Text("Find by number, name or phone") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = c.surface,
                focusedContainerColor = c.surface,
                unfocusedBorderColor = c.outline,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.customers.isEmpty()) {
            EmptyDay(state)
        } else {
            LazyVerticalGrid(
                columns = columns,
                // Room for the selected photo's glow, which would otherwise be cut off at the edges.
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(state.customers.size, key = { "${state.customers[it].date}/${state.customers[it].number}" }) { index ->
                    val customer = state.customers[index]
                    LaunchedEffect(customer) { onLoadThumbnail(customer) }
                    CustomerCard(
                        customer = customer,
                        photo = thumbnails[customer],
                        selected = customer == state.selected,
                        onSheet = isQueued(customer),
                        showDay = state.searching,
                        onClick = { onSelect(customer) },
                        modifier = Modifier.riseIn(index),
                    )
                }
            }
        }
    }
}

private fun subtitle(state: CustomersState): String {
    val n = state.customers.size
    return when {
        state.searching -> if (n == 1) "1 match" else "$n matches"
        n == 1 -> "1 customer"
        else -> "$n customers"
    }
}

@Composable
private fun CustomerCard(
    customer: Customer,
    photo: ImageBitmap?,
    selected: Boolean,
    onSheet: Boolean,
    showDay: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StudioTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val source = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(source)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = "Customer ${customer.label}" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .selectionGlow(selected, shape)
                .clip(shape)
                .background(c.well),
        ) {
            if (photo != null) {
                Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (onSheet) OnSheetBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Column(Modifier.padding(horizontal = 2.dp)) {
            CustomerNumber(customer.label)
            val line = listOfNotNull(customer.name, customer.date.toString().takeIf { showDay }).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, maxLines = 1)
        }
    }
}

@Composable
private fun OnSheetBadge(modifier: Modifier = Modifier) {
    val c = StudioTheme.colors
    Row(
        modifier.clip(CircleShape).background(c.okWash).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✓ on sheet", style = MaterialTheme.typography.labelMedium, color = c.ok)
    }
}

@Composable
private fun EmptyDay(state: CustomersState) {
    val c = StudioTheme.colors
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (state.searching) "No customer matches \"${state.query.trim()}\"." else "No customers on this day yet.",
                style = MaterialTheme.typography.titleMedium,
                color = c.text,
            )
            if (!state.searching && state.date == state.today) {
                Text("Take or import the first photo to start.", style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
            }
        }
    }
}
