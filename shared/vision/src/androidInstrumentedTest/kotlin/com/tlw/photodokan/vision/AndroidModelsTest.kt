package com.tlw.photodokan.vision

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith

/** The models in the APK's assets, on a phone or emulator. */
@RunWith(AndroidJUnit4::class)
class AndroidModelsTest : ModelsContract() {
    override val files = AssetModelFiles(InstrumentationRegistry.getInstrumentation().targetContext)
}
