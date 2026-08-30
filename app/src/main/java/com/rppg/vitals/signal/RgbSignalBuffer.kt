package com.rppg.vitals.signal

import com.rppg.vitals.domain.RgbSample
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe rolling buffer for RGB samples.
 * Maintains a fixed maximum capacity, discarding oldest samples.
 */
class RgbSignalBuffer(private val maxCapacity: Int = 900) { // 30fps * 30s

    private val buffer: ArrayDeque<RgbSample> = ArrayDeque(maxCapacity)
    private val lock = Any()

    /**
     * Add a new sample. If at capacity, removes the oldest.
     */
    fun add(sample: RgbSample) {
        synchronized(lock) {
            if (buffer.size >= maxCapacity) {
                buffer.removeFirst()
            }
            buffer.addLast(sample)
        }
    }

    /**
     * Get a snapshot of the current buffer.
     */
    fun snapshot(): List<RgbSample> {
        synchronized(lock) {
            return buffer.toList()
        }
    }

    /**
     * Get the last N samples, or all available if fewer exist.
     */
    fun lastN(n: Int): List<RgbSample> {
        synchronized(lock) {
            val size = buffer.size
            return if (size <= n) {
                buffer.toList()
            } else {
                buffer.drop(size - n)
            }
        }
    }

    fun size(): Int {
        synchronized(lock) { return buffer.size }
    }

    fun clear() {
        synchronized(lock) { buffer.clear() }
    }

    /**
     * Compute the effective sampling rate (fps) from the timestamps in the buffer.
     */
    fun computeFps(): Double {
        val snap = snapshot()
        if (snap.size < 2) return 30.0 // default
        val durationMs = snap.last().timestampMs - snap.first().timestampMs
        return if (durationMs <= 0) 30.0
        else (snap.size - 1).toDouble() / (durationMs / 1000.0)
    }

    /**
     * Compute the effective face stability (used for motion detection).
     * Returns the mean displacement of the face center across the last N samples.
     */
    fun durationMs(): Long {
        val snap = snapshot()
        if (snap.size < 2) return 0L
        return snap.last().timestampMs - snap.first().timestampMs
    }
}
