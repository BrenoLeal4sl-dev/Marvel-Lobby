package com.example.marvellobby

import com.example.marvellobby.data.api.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ComicVineRequestGateTest {
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
