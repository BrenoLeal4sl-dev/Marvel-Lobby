package com.example.marvellobby

import com.example.marvellobby.data.api.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ComicVineRequestGateTest {
    @Test fun successfulPageScansAreSpacedWithoutBlockingTheCoroutineThread() = runBlocking {
        var clock=0L
        val waits=mutableListOf<Long>()
        val starts=mutableListOf<Long>()
        val gate=ComicVineRequestGate(pause={ millis -> waits.add(millis);clock+=millis*1_000_000L },nanoTime={ clock })
        repeat(3) { gate.execute { starts.add(clock) } }
        assertEquals(listOf(0L,1_000_000_000L,2_000_000_000L),starts)
        assertEquals(listOf(1_000L,1_000L),waits)
    }

    @Test fun canceledRequestDuringSpacingDoesNotReachNetworkOrBlockLaterPages() = runBlocking {
        var clock=0L
        var calls=0
        val waiting=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        val gate=ComicVineRequestGate(pause={ waiting.complete(Unit);release.await() },nanoTime={ clock })
        gate.execute { calls++ }
        val canceled=launch { gate.execute { calls++ } }
        waiting.await()
        canceled.cancelAndJoin()
        assertEquals(1,calls)
        clock=1_000_000_000L
        gate.execute { calls++ }
        assertEquals(2,calls)
    }

    @Test fun firstHostBlockProvidesCooldownAndStopsPrematureRetries() = runBlocking {
        var clock=0L
        var calls=0
        val gate=ComicVineRequestGate(pause={ millis -> clock+=millis*1_000_000L },nanoTime={ clock })
        try {
            gate.execute { calls++;throw ComicVineException("Blocked",ComicVineFailure.ACCESS_BLOCKED) }
            fail("Expected host block")
        } catch(error: ComicVineException) {
            assertEquals(30L,error.retryAfterSeconds)
            assertEquals(ComicVineFailure.ACCESS_BLOCKED,error.failure)
        }
        clock+=15_000_000_000L
        try { gate.execute { calls++ };fail("Expected cooldown") }
        catch(error: ComicVineException) { assertEquals(15L,error.retryAfterSeconds) }
        assertEquals(1,calls)
        clock+=15_000_000_000L
        gate.execute { calls++ }
        assertEquals(2,calls)
    }

    @Test fun canceledSearchWaitingForAnotherRequestNeverUsesNetwork() = runBlocking {
        val gate = ComicVineRequestGate()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val active = launch(start=CoroutineStart.UNDISPATCHED) {
            gate.execute { calls++; release.await() }
        }
        val outdated = launch(start=CoroutineStart.UNDISPATCHED) {
            gate.execute { calls++ }
        }
        outdated.cancelAndJoin()
        release.complete(Unit)
        active.join()
        assertEquals(1,calls)
        gate.execute { calls++ }
        assertEquals(2,calls)
    }

    @Test fun retryAfterStopsNetworkUntilDeadlineAndHandlesNegativeMonotonicClock() = runBlocking {
        var clock = -100_000_000_000L
        val gate = ComicVineRequestGate { clock }
        var calls = 0
        try {
            gate.execute { calls++; throw ComicVineException("Limited",ComicVineFailure.RATE_LIMIT,90) }
            fail("Expected failure")
        } catch (error: ComicVineException) { assertEquals(ComicVineFailure.RATE_LIMIT,error.failure) }
        clock += 30_000_000_000L
        try {
            gate.execute { calls++ }
            fail("Expected cooldown")
        } catch (error: ComicVineException) { assertEquals(60L,error.retryAfterSeconds) }
        assertEquals(1,calls)
        clock += 60_000_000_000L
        gate.execute { calls++ }
        assertEquals(2,calls)
    }

    @Test fun ordinaryServerFailureDoesNotBlockAllOtherEndpoints() = runBlocking {
        val gate = ComicVineRequestGate()
        try { gate.execute { throw ComicVineException("HTTP 500",ComicVineFailure.HTTP) } }
        catch (_: ComicVineException) { }
        assertEquals("next endpoint",gate.execute { "next endpoint" })
    }
}
