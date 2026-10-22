package com.tlw.photodokan.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The app's top-level screens, in the order they appear in the navigation. */
enum class Destination(val label: String, val icon: ImageVector) {
    Customers("Customers", Icons.Filled.Person),
    Print("Print", PrintIcon),
    Settings("Settings", Icons.Filled.Settings),
}

/** Material "print" glyph; it isn't in the core icon set. */
private val PrintIcon: ImageVector = ImageVector.Builder("Print", 24.dp, 24.dp, 24f, 24f)
    .addPath(
        pathData = addPathNodes(
            "M19,8H5c-1.66,0 -3,1.34 -3,3v6h4v4h12v-4h4v-6c0,-1.66 -1.34,-3 -3,-3zM16,19H8v-5h8v5z" +
                "M19,12c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1 1,0.45 1,1 -0.45,1 -1,1zM18,3H6v4h12V3z",
        ),
        fill = SolidColor(Color.Black),
    )
    .build()
