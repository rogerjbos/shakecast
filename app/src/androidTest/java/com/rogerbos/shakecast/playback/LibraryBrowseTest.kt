package com.rogerbos.shakecast.playback

import android.content.ComponentName
import android.os.Environment
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import com.rogerbos.shakecast.MainActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rogerbos.shakecast.data.db.EpisodeEntity
import com.rogerbos.shakecast.data.db.PodcastEntity
import com.rogerbos.shakecast.data.db.ShakecastDatabase
import com.rogerbos.shakecast.domain.model.AdSource
import com.rogerbos.shakecast.domain.model.DownloadState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Connects a real media browser to the service, the way Android Auto does.
 * Seeds one show with one downloaded (silent) episode so the test does not
 * depend on what the device happens to have.
 */
@RunWith(AndroidJUnit4::class)
class LibraryBrowseTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var db: ShakecastDatabase
    private lateinit var audio: File
    private var podcastId = 0L
    private var episodeId = 0L
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun seed(): Unit = runBlocking {
        audio = File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "test/silence.m4a").apply {
            parentFile?.mkdirs()
            instrumentation.context.assets.open("silence.m4a").use { input -> outputStream().use { input.copyTo(it) } }
        }
        db = Room.databaseBuilder(context, ShakecastDatabase::class.java, "shakecast.db").build()
        podcastId = db.podcastDao().insert(
            PodcastEntity(
                id = 0, feedUrl = FEED, itunesId = null, title = "Browse Test Show", author = "Test", description = "",
                artworkUrl = "", subscribedAt = 1, lastRefreshedAt = null, etag = null, lastModified = null,
                autoSkipAds = false, autoDownload = false, subscriptionUpdatedAtMs = 1, syncDirty = false
            )
        ).takeIf { it > 0 } ?: db.podcastDao().getByFeedUrl(FEED)!!.id
        db.episodeDao().insertIgnore(
            listOf(
                EpisodeEntity(
                    id = 0, podcastId = podcastId, guid = "browse-test-1", title = "Ninety seconds of silence", description = "",
                    pubDateMs = System.currentTimeMillis(), durationSec = 90, enclosureUrl = "https://example.invalid/silence.m4a",
                    enclosureType = "audio/mp4", enclosureLength = audio.length(), transcriptUrl = null, transcriptType = null,
                    chaptersUrl = null, chaptersJson = "[]", downloadState = DownloadState.DOWNLOADED, localFilePath = audio.absolutePath,
                    downloadedAt = 1, positionMs = 0, isPlayed = false, playedAt = null, lastPlayedAt = null,
                    adSource = AdSource.NONE, adBlocksJson = null, adResolvedAt = 1, stateUpdatedAtMs = 0, syncDirty = false
                )
            )
        )
        episodeId = db.episodeDao().getByGuid(podcastId, "browse-test-1")!!.id
    }

    @After
    fun cleanUp() {
        scenario?.close()
        runBlocking { db.podcastDao().delete(podcastId) }
        db.close()
        audio.delete()
    }

    /** MediaBrowser must be used from the thread it was built on; the futures can be awaited anywhere. */
    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun browser(): MediaBrowser {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        return onMain { MediaBrowser.Builder(context, token).buildAsync() }.get(10, TimeUnit.SECONDS)
    }

    /** A new controller receives the session's current state on connect, free of any local masking. */
    private fun freshPosition(): Long {
        val fresh = browser()
        try {
            return onMain { fresh.currentPosition }
        } finally {
            onMain { fresh.release() }
        }
    }

    private fun MediaBrowser.children(parentId: String) =
        onMain { getChildren(parentId, 0, 50, null) }.get(10, TimeUnit.SECONDS).value!!

    @Test
    fun browserSeesRootFoldersShowsAndDownloads() {
        val browser = browser()
        try {
            val rootResult = onMain { browser.getLibraryRoot(null) }.get(10, TimeUnit.SECONDS)
            assertEquals("library root result code", 0, rootResult.resultCode)
            val root = rootResult.value!!
            assertEquals(LibraryTree.ROOT, root.mediaId)

            val folders = browser.children(root.mediaId)
            assertEquals(listOf(LibraryTree.DOWNLOADS, LibraryTree.QUEUE, LibraryTree.SHOWS), folders.map { it.mediaId })

            val shows = browser.children(LibraryTree.SHOWS)
            val show = shows.first { it.mediaId == LibraryTree.SHOW_PREFIX + podcastId }
            assertTrue(show.mediaMetadata.isBrowsable == true)
            assertEquals("Browse Test Show", show.mediaMetadata.title.toString())

            val episodes = browser.children(show.mediaId)
            assertEquals(listOf(episodeId.toString()), episodes.map { it.mediaId })
            assertTrue(episodes[0].mediaMetadata.isPlayable == true)

            val downloads = browser.children(LibraryTree.DOWNLOADS)
            assertTrue(downloads.any { it.mediaId == episodeId.toString() })
        } finally {
            onMain { browser.release() }
        }
    }

    @Test
    fun controllerNextAndPreviousBecomeSkips() {
        // Android 15 denies audio focus to apps with no visible activity, so bring the app to the front first.
        scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(1_500)
        val browser = browser()
        try {
            val item = browser.children(LibraryTree.DOWNLOADS).first { it.mediaId == episodeId.toString() }
            onMain {
                browser.setMediaItem(item)
                browser.prepare()
                browser.play()
            }
            Thread.sleep(4_000)
            assertTrue("playback should have started", onMain { browser.isPlaying })
            val before = freshPosition()
            onMain { browser.seekToNext() }
            Thread.sleep(2_000)
            val after = freshPosition()
            assertTrue("expected a skip of about 30 s, got ${after - before} ms", after - before in 25_000..40_000)
            onMain { browser.seekToPrevious() }
            Thread.sleep(2_000)
            val back = freshPosition()
            assertTrue("expected a skip back of about 15 s, got ${after - back} ms", after - back in 10_000..20_000)
            onMain { browser.stop() }
        } finally {
            onMain { browser.release() }
        }
    }

    private companion object {
        const val FEED = "https://example.invalid/browse-test-feed.xml"
    }
}
