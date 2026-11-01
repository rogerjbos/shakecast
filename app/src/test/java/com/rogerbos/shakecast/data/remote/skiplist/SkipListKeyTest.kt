package com.rogerbos.shakecast.data.remote.skiplist

import org.junit.Assert.assertEquals
import org.junit.Test

class SkipListKeyTest {
    @Test
    fun matchesPythonSafeFilename() {
        val key = SkipListKey.forEpisode(
            "Wealthion - Be Financially Resilient",
            "Lobo Tiggre: Why This War Could Put Copper on a Path to \$10,000"
        )
        assertEquals("Wealthion_-_Be_Financially_Resilient_Lobo_Tiggre_Why_This_War_Could_Put_Copper_o", key)
    }

    @Test
    fun fileNameAppendsSuffix() {
        assertEquals("A_B_skiplist.json", SkipListKey.fileName("A", "B"))
    }
}
