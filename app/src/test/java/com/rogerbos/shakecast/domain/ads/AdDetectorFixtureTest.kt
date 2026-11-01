package com.rogerbos.shakecast.domain.ads

import com.rogerbos.shakecast.data.remote.skiplist.SkipListDto
import com.rogerbos.shakecast.data.remote.transcript.TranscriptParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Re-derives every bundled podcast3r skip-list from its Whisper transcript and
 * checks the Kotlin port produces identical blocks.
 *
 * The `_whisper.json` transcripts are third-party content and are not committed;
 * the test is skipped when they are absent.
 */
class AdDetectorFixtureTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val stems = listOf(
        "Wealthion_-_Be_Financially_Resilient_Lobo_Tiggre_Why_This_War_Could_Put_Copper_o",
        "Wealthion_-_Be_Financially_Resilient_Jacob_Shapiro_The_Iran_War_Gives_Putin_a_Li",
        "Wealthion_-_Be_Financially_Resilient_Don_Durrett_Wall_Street_Is_Ignoring_the_Che",
        "Maggie_Lake_Talking_Markets_Episode_28_The_New_Fed_Chair_is_Oil_With_Peter_Boock",
        "Maggie_Lake_Talking_Markets_Episode_27_How_High_Can_Bond_Yields_Go_With_Blake_Mo",
        "The_DeFi_Report_Saylor_is_Back_How_does_this_impact_market_structure",
        "Empire_World_History_Christiane_Amanpour_On_Iran_Trumps_Miscalculations",
        "Palisades_Gold_Radio_Gary_Wagnar_Ominous_Sign_for_Gold_The_Iran_War_Gold_Miners",
        "Thoughtful_Money_with_Adam_Taggart_The_Market_Just_Broke_Below_A_Critical_Suppor"
    )

    private fun resourceOrNull(name: String): String? =
        javaClass.classLoader?.getResourceAsStream("fixtures/$name")?.bufferedReader()?.use { it.readText() }

    private fun resource(name: String): String = checkNotNull(resourceOrNull(name)) { "missing fixture $name" }

    @Test
    fun portMatchesPythonSkipLists() {
        assumeTrue(
            "Whisper transcripts are not committed; copy them from podcast3r/transcripts to run this test",
            stems.all { resourceOrNull("${it}_whisper.json") != null }
        )
        for (stem in stems) {
            val segments = TranscriptParser.parseJson(resource("${stem}_whisper.json"))
            val expected = json.decodeFromString<SkipListDto>(resource("${stem}_skiplist.json"))

            val actual = AdDetector.detect(segments)

            assertEquals("$stem totalDuration", expected.totalDuration, actual.totalDuration, 1e-6)
            assertEquals("$stem block count", expected.adBlocks.size, actual.adBlocks.size)
            expected.adBlocks.zip(actual.adBlocks).forEachIndexed { i, (e, a) ->
                assertEquals("$stem block $i start", e.start, a.start, 1e-6)
                assertEquals("$stem block $i end", e.end, a.end, 1e-6)
            }
            assertEquals("$stem content ranges", expected.contentRanges.size, actual.contentRanges.size)
            assertEquals("$stem adTimeSaved", expected.adTimeSaved, actual.adTimeSaved, 0.01)
        }
    }
}
