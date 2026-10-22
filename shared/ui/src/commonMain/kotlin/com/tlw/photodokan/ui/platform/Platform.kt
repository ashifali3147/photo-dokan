package com.tlw.photodokan.ui.platform

import androidx.compose.ui.graphics.ImageBitmap
import com.tlw.photodokan.core.library.StudioDate

/** Today's date on this device's clock. */
expect fun today(): StudioDate

/** Encodes an image as JPEG, e.g. the final.jpg kept in the library. */
expect fun encodeJpeg(image: ImageBitmap, quality: Int = 90): ByteArray
