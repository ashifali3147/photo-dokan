package com.tlw.photodokan.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.components.pressScale
import com.tlw.photodokan.ui.theme.StudioTheme

/**
 * The side rail on a wide window: the app's mark, then one pill per screen. Settings sits at the
 * bottom, away from the work. [waiting] is how many customers are queued to print, shown on Print.
 */
@Composable
fun StudioRail(current: Destination, onSelect: (Destination) -> Unit, waiting: Int, modifier: Modifier = Modifier) {
    val c = StudioTheme.colors
    Row(modifier.fillMaxHeight()) {
        Column(
            Modifier.width(96.dp).fillMaxHeight().background(c.rail).padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Mark()
            Box(Modifier.height(16.dp))
            for (destination in Destination.entries) {
                if (destination == Destination.Settings) Box(Modifier.weight(1f))
                NavItem(destination, destination == current, { onSelect(destination) }, badge(destination, waiting), Modifier.width(76.dp))
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
    }
}

/** The bar along the bottom of a phone: the same screens as [StudioRail], on white. */
@Composable
fun StudioBottomBar(current: Destination, onSelect: (Destination) -> Unit, waiting: Int, modifier: Modifier = Modifier) {
    val c = StudioTheme.colors
    Column(modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (destination in Destination.entries) {
                NavItem(destination, destination == current, { onSelect(destination) }, badge(destination, waiting), Modifier.weight(1f))
            }
        }
    }
}

private fun badge(destination: Destination, waiting: Int): Int? = waiting.takeIf { destination == Destination.Print && it > 0 }

/** The app's mark: its initials on a solid block, the one dark (or, in the dark theme, light) thing in the rail. */
@Composable
private fun Mark() {
    val c = StudioTheme.colors
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.strong), contentAlignment = Alignment.Center) {
        Text("PD", style = StudioTheme.type.numberSmall.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize), color = c.onStrong)
    }
}

/**
 * One screen in the rail or bar: icon over label in a pill. The chosen one is washed in the accent;
 * the wash fades in rather than jumping. [badge] is a count drawn on the icon.
 */
@Composable
private fun NavItem(destination: Destination, selected: Boolean, onClick: () -> Unit, badge: Int?, modifier: Modifier) {
    val c = StudioTheme.colors
    val source = remember { MutableInteractionSource() }
    val wash by animateColorAsState(if (selected) c.accentWash else Color.Transparent, tween(200), label = "nav wash")
    val tint by animateColorAsState(if (selected) c.accentText else c.textMuted, tween(200), label = "nav tint")
    Column(
        modifier
            .height(64.dp)
            .pressScale(source)
            .clip(RoundedCornerShape(16.dp))
            .background(wash)
            .selectable(selected, interactionSource = source, indication = null, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(destination.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            if (badge != null) Badge(badge, Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-6).dp))
        }
        Text(destination.label, style = MaterialTheme.typography.labelMedium, color = tint, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun Badge(count: Int, modifier: Modifier) {
    val c = StudioTheme.colors
    Text(
        if (count > 99) "99+" else "$count",
        modifier
            .widthIn(min = 20.dp)
            .height(20.dp)
            .clip(CircleShape)
            .background(c.accentBottom)
            .padding(horizontal = 5.dp),
        style = StudioTheme.type.numberSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
        color = c.onAccent,
        textAlign = TextAlign.Center,
    )
}
