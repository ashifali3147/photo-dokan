package com.tlw.photodokan.vision

import com.tlw.photodokan.core.matte.Matte
import kotlin.test.Test
import kotlin.test.assertEquals

class ModNetTest {

    @Test
    fun theShorterSideBecomes512AndBothAMultipleOf32() {
        assertEquals(512 to 672, ModNet.inputSize(1920, 2560)) // 682.7 rounded down to 672
        assertEquals(672 to 512, ModNet.inputSize(4000, 3000))
        assertEquals(512 to 512, ModNet.inputSize(300, 300), "small photos are enlarged")
        assertEquals(512 to 2048, ModNet.inputSize(100, 400))
    }

    @Test
    fun theInputIsRedGreenBlueFromMinusOneToOne() {
        val colour = (0xff shl 24) or (255 shl 16) or (0 shl 8) or 51
        val input = ModNet.input(IntArray(1024 * 1024) { colour }, 1024, 1024)
        assertEquals(512, input.width)
        assertEquals(512, input.height)
        val plane = 512 * 512
        assertEquals(1f, input.pixels[0], "red")
        assertEquals(-1f, input.pixels[plane], "green")
        assertEquals(51 / 127.5f - 1, input.pixels[2 * plane], "blue")
    }

    @Test
    fun theOutputBecomesAMatte() {
        val matte = ModNet.matte(floatArrayOf(0f, 0.5f, 1f, 1.2f, -0.1f, 0.25f), 3, 2)
        assertEquals(Matte(3, 2, byteArrayOf(0, -128, -1, -1, 0, 64)), matte)
    }
}
