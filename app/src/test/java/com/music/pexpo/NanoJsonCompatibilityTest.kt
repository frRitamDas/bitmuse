package com.music.pexpo

import com.grack.nanojson.JsonArray
import org.junit.Assert.assertNotNull
import org.junit.Test

class NanoJsonCompatibilityTest {
    @Test
    fun `NewPipe NanoJSON exposes its stream bridge method`() {
        val methodName = "streamAs" + "JsonObjects"
        val method = JsonArray::class.java.methods.firstOrNull { it.name == methodName }
        assertNotNull("The NewPipe-compatible NanoJSON build must expose the stream bridge", method)
    }
}
