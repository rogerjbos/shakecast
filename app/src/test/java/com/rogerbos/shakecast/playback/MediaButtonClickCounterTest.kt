package com.rogerbos.shakecast.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaButtonClickCounterTest {

    private class FakeScheduler {
        var pending: (() -> Unit)? = null
        fun schedule(action: () -> Unit): MediaButtonClickCounter.Cancellable {
            pending = action
            return MediaButtonClickCounter.Cancellable { if (pending === action) pending = null }
        }
        fun fire() { pending?.also { pending = null }?.invoke() }
    }

    @Test
    fun singleClickResolvesToOne() {
        val s = FakeScheduler()
        val resolved = mutableListOf<Int>()
        val c = MediaButtonClickCounter(schedule = { _, a -> s.schedule(a) }, onResolved = { resolved += it })
        c.onClick()
        s.fire()
        assertEquals(listOf(1), resolved)
    }

    @Test
    fun rapidClicksAccumulate() {
        val s = FakeScheduler()
        val resolved = mutableListOf<Int>()
        val c = MediaButtonClickCounter(schedule = { _, a -> s.schedule(a) }, onResolved = { resolved += it })
        c.onClick(); c.onClick(); c.onClick()
        s.fire()
        assertEquals(listOf(3), resolved)
        c.onClick(); c.onClick()
        s.fire()
        assertEquals(listOf(3, 2), resolved)
    }

    @Test
    fun flushResolvesImmediately() {
        val s = FakeScheduler()
        val resolved = mutableListOf<Int>()
        val c = MediaButtonClickCounter(schedule = { _, a -> s.schedule(a) }, onResolved = { resolved += it })
        c.onClick(); c.onClick()
        c.flush()
        assertEquals(listOf(2), resolved)
        s.fire()
        assertEquals(listOf(2), resolved)
    }
}
