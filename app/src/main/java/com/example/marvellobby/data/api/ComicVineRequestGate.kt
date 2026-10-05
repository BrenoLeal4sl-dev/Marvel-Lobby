package com.example.marvellobby.data.api

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Waiting requests are cancellable; a refused connection must not trigger a request burst. */
internal class ComicVineRequestGate(
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val mutex = Mutex()
    private var nextRequestAt: Long? = null
    private var blockedUntil = 0L
    private var blockedError: ComicVineException? = null

    suspend fun <T> execute(request: suspend () -> T): T = mutex.withLock {
        currentCoroutineContext().ensureActive()
        blockedError?.let { previous ->
            val remaining = blockedUntil - nanoTime()
            if (remaining > 0) {
                val seconds = (remaining + 999_999_999L) / 1_000_000_000L
                throw ComicVineException("${previous.message} Nova tentativa disponível em ${seconds}s.", previous.failure, seconds)
            }
        }
        nextRequestAt?.let { deadline ->
            val remaining = deadline - nanoTime()
            if (remaining > 0) pause((remaining + 999_999L) / 1_000_000L)
        }
        currentCoroutineContext().ensureActive()
        try {
            request().also { currentCoroutineContext().ensureActive() }
        } catch (error: ComicVineException) {
            currentCoroutineContext().ensureActive()
            if (error.failure.stopsRequests) {
                val seconds = error.retryAfterSeconds?.coerceIn(30, 3600) ?: 30L
                blockedUntil = nanoTime() + seconds * 1_000_000_000L
                blockedError = error
                // The first refusal also supplies a deadline for the UI, not only subsequent attempts.
                throw ComicVineException(error.message.orEmpty(), error.failure, seconds).apply { initCause(error) }
            }
            throw error
        } finally {
            // Publisher filtering may scan several pages. Serialize and space those requests too.
            nextRequestAt = nanoTime() + 1_000_000_000L
        }
    }
}
