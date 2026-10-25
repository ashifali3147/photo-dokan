package com.tlw.photodokan.core.model

import com.tlw.photodokan.core.geometry.SizeMm
import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.library.StudioDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SerializationTest {

    private inline fun <reified T> roundTrip(value: T): T =
        Json.decodeFromString<T>(Json.encodeToString(value))

    @Test
    fun editedRecipeSurvivesRoundTrip() {
        val recipe = EditRecipe(
            presetId = PhotoSizePreset.PASSPORT_35X45.id,
            crop = CropConfig(centerX = 0.42, centerY = 0.55, scale = 1.8, rotationDegrees = -3.5),
            exposure = 0.2,
            contrast = -0.15,
            warmth = 0.1,
            tint = -0.05,
            saturation = -0.3,
            background = BackgroundConfig(BackgroundMode.REPLACE, blurStrength = 0.0, replaceColorArgb = BackgroundConfig.LIGHT_BLUE),
            retouch = 0.25,
        )
        assertEquals(recipe, roundTrip(recipe))
    }

    @Test
    fun recipeWithOnlyPresetLoadsDefaults() {
        // An old recipe.json written before later fields existed must still open for reprint.
        val recipe = Json.decodeFromString<EditRecipe>("""{"presetId":"studio-slot"}""")
        assertEquals(EditRecipe(presetId = "studio-slot"), recipe)
        assertEquals(BackgroundMode.BLUR, recipe.background.mode)
    }

    @Test
    fun savedRecipeFormatStaysReadable() {
        // Pins field and enum names: renaming either would break every recipe.json in the library.
        val json = """
            {"presetId":"passport-35x45","crop":{"centerX":0.4,"centerY":0.6,"scale":2.0,"rotationDegrees":1.5},
             "exposure":0.1,"contrast":0.2,"warmth":-0.1,"tint":0.15,"saturation":0.3,
             "background":{"mode":"REPLACE","blurStrength":0.5,"replaceColorArgb":4294967295},"retouch":0.2}
        """.trimIndent()
        val expected = EditRecipe(
            presetId = "passport-35x45",
            crop = CropConfig(0.4, 0.6, 2.0, 1.5),
            exposure = 0.1,
            contrast = 0.2,
            warmth = -0.1,
            tint = 0.15,
            saturation = 0.3,
            background = BackgroundConfig(BackgroundMode.REPLACE, 0.5, BackgroundConfig.WHITE),
            retouch = 0.2,
        )
        assertEquals(expected, Json.decodeFromString<EditRecipe>(json))
    }

    @Test
    fun decodingStillValidatesRanges() {
        assertFailsWith<IllegalArgumentException> {
            Json.decodeFromString<EditRecipe>("""{"presetId":"studio-slot","exposure":3.0}""")
        }
        assertFailsWith<IllegalArgumentException> {
            Json.decodeFromString<Order>("""{"id":"A","customerLabel":"0001","copies":0}""")
        }
        assertFailsWith<IllegalArgumentException> {
            Json.decodeFromString<StudioDate>("""{"year":2026,"month":2,"day":30}""")
        }
    }

    @Test
    fun customerSurvivesRoundTrip() {
        val named = Customer(StudioDate(2026, 10, 22), number = 7, name = "Ramesh", phone = "9876543210")
        val anonymous = Customer(StudioDate(2028, 2, 29), number = 12)
        assertEquals(named, roundTrip(named))
        assertEquals(anonymous, roundTrip(anonymous))
    }

    @Test
    fun orderAndSheetStateSurviveRoundTrip() {
        val order = Order(id = "2026-10-22-0007", customerLabel = "0007 Ramesh", copies = 10)
        val partUsed = SheetState(usedSlots = setOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13))
        assertEquals(order, roundTrip(order))
        assertEquals(partUsed, roundTrip(partUsed))
        assertEquals(SheetState.FRESH, roundTrip(SheetState.FRESH))
    }

    @Test
    fun presetsAndTemplatesSurviveRoundTrip() {
        for (preset in PhotoSizePreset.DEFAULTS) assertEquals(preset, roundTrip(preset))

        val template = SheetTemplate.STUDIO_A4_5X8
        assertEquals(template, roundTrip(template))

        val custom = SheetTemplate("4 × 6 stamps", SizeMm(101.6, 152.4), SizeMm(20.0, 25.0), 4, 5, 1.5, cutLines = false)
        assertEquals(custom, roundTrip(custom))
    }
}
