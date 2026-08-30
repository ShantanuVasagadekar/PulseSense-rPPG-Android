package com.rppg.vitals.signal

import android.util.Log
import com.rppg.vitals.domain.RgbSample
import com.rppg.vitals.domain.SignalQuality
import kotlin.math.*

/**
 * POS (Plane-Orthogonal-to-Skin) rPPG algorithm with Advanced Harmonic Analysis.
 *
 * References:
 *   1. Wang, W., den Brinker, A. C., Stuijk, S., & de Haan, G. (2017).
 *      Algorithmic principles of remote PPG. IEEE TBME, 64(7), 1479–1491.
 *   2. ubicomplab/rPPG-Toolbox: evaluation/post_process.py
 *
 * Fixes for 169 BPM / Harmonic doubling:
 *   - Constrains default cardiac band to 0.75–2.50 Hz (45–150 BPM) matching rPPG-Toolbox.
 *   - Implements multi-peak subharmonic detection to identify and correct for 2nd/3rd harmonics.
 *   - Applies 3-point parabolic spectral peak interpolation for sub-bin precision.
 *   - Zero-phase Butterworth filtering with verified biquad coefficients.
 */
class PosRppgEngine {

    companion object {
        private const val TAG = "RPPG_DIAG"

        /** Sliding window size in seconds (matches original paper/repo). */
        const val WIN_SEC = 1.6

        /** Physiological bandpass limits in Hz (matching rPPG-Toolbox post_process.py). */
        const val LOW_PASS_HZ = 0.75   // ~45 BPM
        const val HIGH_PASS_HZ = 2.50  // ~150 BPM (Standard physiological resting limit)
        const val EXTENDED_HIGH_PASS_HZ = 3.0 // ~180 BPM for elevated/exercise HR

        /** Minimum number of samples needed to compute a valid BPM. */
        const val MIN_SAMPLES_FOR_BPM = 60

        /** Sub-harmonic power threshold: if peak at f/2 has >= 15% of max power, select f/2 as fundamental. */
        const val SUBHARMONIC_POWER_RATIO = 0.15

        /** Outlier rejection: max delta from running median. */
        const val BPM_OUTLIER_THRESHOLD = 20.0

        /** Median window for stability. */
        const val MEDIAN_WINDOW = 5
    }

    private val recentBpmBuffer = ArrayDeque<Double>(MEDIAN_WINDOW)
    private var smoothedBpm: Double = 0.0

    data class Diagnostics(
        val peakFreqHz: Double,
        val rawBpm: Double,
        val isHarmonicCorrected: Boolean,
        val fundamentalFreqHz: Double,
        val snrDb: Double,
        val confidence: Double,
        val effectiveFps: Double
    )

    data class RppgResult(
        val bpm: Double,
        val pulseSignal: DoubleArray,
        val signalQuality: SignalQuality,
        val confidence: Double,
        val diagnostics: Diagnostics
    )

    /**
     * Process a buffer of RGB samples and return the current BPM estimate.
     *
     * @param samples  Rolling buffer of [RgbSample]
     * @param fps      Effective frame rate of the buffer
     */
    fun process(samples: List<RgbSample>, fps: Double): RppgResult? {
        if (samples.size < MIN_SAMPLES_FOR_BPM || fps <= 5.0) return null

        val n = samples.size
        val r = DoubleArray(n) { samples[it].r }
        val g = DoubleArray(n) { samples[it].g }
        val b = DoubleArray(n) { samples[it].b }

        // ── Step 1: POS projection with sliding window ──────────────
        val winLen = max(2, ceil(WIN_SEC * fps).toInt())
        val h = DoubleArray(n)

        for (idx in winLen until n) {
            val start = idx - winLen
            val rWin = r.sliceArray(start until idx)
            val gWin = g.sliceArray(start until idx)
            val bWin = b.sliceArray(start until idx)

            val rMean = rWin.average().coerceAtLeast(1e-6)
            val gMean = gWin.average().coerceAtLeast(1e-6)
            val bMean = bWin.average().coerceAtLeast(1e-6)

            // Normalize each channel by its window mean (chrominance normalization)
            val cn0 = DoubleArray(winLen) { rWin[it] / rMean }  // R̃
            val cn1 = DoubleArray(winLen) { gWin[it] / gMean }  // G̃
            val cn2 = DoubleArray(winLen) { bWin[it] / bMean }  // B̃

            // POS projection matrix S = [[0,1,-1],[-2,1,1]] × [R̃; G̃; B̃]
            val s0 = DoubleArray(winLen) { cn1[it] - cn2[it] }          // row 0
            val s1 = DoubleArray(winLen) { -2 * cn0[it] + cn1[it] + cn2[it] } // row 1

            val std0 = s0.std().coerceAtLeast(1e-9)
            val std1 = s1.std().coerceAtLeast(1e-9)

            // h = s0 + (std0/std1) * s1, then subtract mean
            val hWin = DoubleArray(winLen) { s0[it] + (std0 / std1) * s1[it] }
            val hMean = hWin.average()
            for (i in 0 until winLen) {
                h[start + i] += hWin[i] - hMean
            }
        }

        // ── Step 2: Smooth detrending (λ=100, matches rPPG-Toolbox) ──
        val detrended = detrend(h, lambda = 100.0)

        // ── Step 3: Bandpass Butterworth filter ──────────────────────
        val filtered = bandpassFilter(detrended, fps, LOW_PASS_HZ, HIGH_PASS_HZ)

        // ── Step 4: Spectral analysis with harmonic resolution ───────
        val spectralResult = estimateBpmWithHarmonics(filtered, fps)
        val rawBpm = spectralResult.selectedBpm
        val confidence = spectralResult.confidence

        // ── Step 5: Outlier rejection + smoothing ────────────────────
        val finalBpm = updateSmoothedBpm(rawBpm, confidence)

        // ── Step 6: Signal quality classification ────────────────────
        val quality = classifyQuality(filtered, confidence, spectralResult.snrDb)

        val diag = Diagnostics(
            peakFreqHz = spectralResult.maxPeakFreqHz,
            rawBpm = rawBpm,
            isHarmonicCorrected = spectralResult.isHarmonicCorrected,
            fundamentalFreqHz = spectralResult.selectedFreqHz,
            snrDb = spectralResult.snrDb,
            confidence = confidence,
            effectiveFps = fps
        )

        safeLogD(TAG, "[rPPG] FPS=${"%.1f".format(fps)}, Samples=$n, Peak=${"%.2f".format(diag.peakFreqHz)}Hz (${(diag.peakFreqHz*60).toInt()} BPM), Corrected=${diag.isHarmonicCorrected}, Fund=${"%.2f".format(diag.fundamentalFreqHz)}Hz -> Final HR=${"%.1f".format(finalBpm)} BPM, Conf=${"%.2f".format(confidence)}, SNR=${"%.1f".format(diag.snrDb)}dB")

        return RppgResult(
            bpm = finalBpm,
            pulseSignal = filtered,
            signalQuality = quality,
            confidence = confidence,
            diagnostics = diag
        )
    }

    fun reset() {
        recentBpmBuffer.clear()
        smoothedBpm = 0.0
    }

    // ─────────────────────── Signal Processing ───────────────────────

    /**
     * Sparse-regularization detrending (λ=100).
     * Matches the detrend() function in rPPG-Toolbox unsupervised_methods/utils.py.
     */
    private fun detrend(signal: DoubleArray, lambda: Double): DoubleArray {
        val n = signal.size
        if (n < 3) return signal.copyOf()

        val lambda2 = lambda * lambda
        val d0 = DoubleArray(n)
        val d1 = DoubleArray(n - 1)
        val d2 = DoubleArray(n - 2)

        for (i in 0 until n) {
            d0[i] = 1.0 + lambda2 * when (i) {
                0, n - 1 -> 1.0
                1, n - 2 -> 5.0
                else -> 6.0
            }
        }
        for (i in 0 until n - 1) {
            d1[i] = lambda2 * if (i == 0 || i == n - 2) -2.0 else -4.0
        }
        for (i in 0 until n - 2) {
            d2[i] = lambda2 * 1.0
        }

        return solvePentadiagonal(d0, d1, d2, signal)
    }

    private fun solvePentadiagonal(
        d0: DoubleArray, d1: DoubleArray, d2: DoubleArray,
        b: DoubleArray
    ): DoubleArray {
        val n = d0.size
        val a0 = d0.copyOf()
        val a1 = d1.copyOf()
        val a2 = d2.copyOf()
        val rhs = b.copyOf()

        // Forward elimination
        for (i in 0 until n - 1) {
            if (abs(a0[i]) < 1e-15) continue
            val f1 = a1[i] / a0[i]
            a0[i + 1] -= f1 * a1[i]
            if (i + 1 < n - 1) a1[i + 1] -= f1 * a2[i]
            rhs[i + 1] -= f1 * rhs[i]

            if (i < n - 2) {
                val f2 = a2[i] / a0[i]
                a1[i + 1] -= f2 * a1[i]
                a0[i + 2] -= f2 * a2[i]
                rhs[i + 2] -= f2 * rhs[i]
            }
        }

        // Back substitution
        val x = DoubleArray(n)
        x[n - 1] = rhs[n - 1] / a0[n - 1].coerceAtLeast(1e-15)
        if (n > 1) x[n - 2] = (rhs[n - 2] - a1[n - 2] * x[n - 1]) / a0[n - 2].coerceAtLeast(1e-15)
        for (i in n - 3 downTo 0) {
            x[i] = (rhs[i] - a1[i] * x[i + 1] - a2[i] * x[i + 2]) / a0[i].coerceAtLeast(1e-15)
        }

        return DoubleArray(n) { b[it] - x[it] }
    }

    /**
     * Zero-phase 2nd-order Butterworth bandpass filter.
     */
    private fun bandpassFilter(
        signal: DoubleArray, fs: Double, low: Double, high: Double
    ): DoubleArray {
        if (signal.size < 10) return signal.copyOf()
        val (b, a) = butterBandpass(low, high, fs)
        return filtfilt(b, a, signal)
    }

    private fun butterBandpass(fLow: Double, fHigh: Double, fs: Double): Pair<DoubleArray, DoubleArray> {
        val f0 = (fLow + fHigh) / 2.0
        val bw = (fHigh - fLow)
        val w0 = 2.0 * PI * (f0 / fs)
        val q = max(0.5, f0 / bw)
        val alpha = sin(w0) / (2.0 * q)

        val b0 = alpha
        val b1 = 0.0
        val b2 = -alpha
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cos(w0)
        val a2 = 1.0 - alpha

        return Pair(
            doubleArrayOf(b0 / a0, b1 / a0, b2 / a0),
            doubleArrayOf(1.0, a1 / a0, a2 / a0)
        )
    }

    private fun filtfilt(b: DoubleArray, a: DoubleArray, x: DoubleArray): DoubleArray {
        val padLen = min(3 * (max(b.size, a.size) - 1), x.size - 1)
        val padded = DoubleArray(x.size + 2 * padLen)
        for (i in 0 until padLen) {
            padded[i] = 2 * x[0] - x[padLen - i]
        }
        x.copyInto(padded, padLen)
        for (i in 0 until padLen) {
            padded[x.size + padLen + i] = 2 * x.last() - x[x.size - 2 - i]
        }

        val forward = iirFilter(b, a, padded)
        val reversed = forward.reversedArray()
        val backward = iirFilter(b, a, reversed)
        val result = backward.reversedArray()

        return result.sliceArray(padLen until padLen + x.size)
    }

    private fun iirFilter(b: DoubleArray, a: DoubleArray, x: DoubleArray): DoubleArray {
        val n = x.size
        val y = DoubleArray(n)
        val nb = b.size
        val na = a.size
        val a0 = a[0].coerceAtLeast(1e-15)

        for (i in 0 until n) {
            var acc = 0.0
            for (j in 0 until nb) {
                if (i - j >= 0) acc += b[j] * x[i - j]
            }
            for (j in 1 until na) {
                if (i - j >= 0) acc -= a[j] * y[i - j]
            }
            y[i] = acc / a0
        }
        return y
    }

    // ─────────────────────── Spectral & Harmonic Analysis ───────────────────────

    private data class SpectralAnalysisResult(
        val maxPeakFreqHz: Double,
        val selectedFreqHz: Double,
        val selectedBpm: Double,
        val isHarmonicCorrected: Boolean,
        val confidence: Double,
        val snrDb: Double
    )

    /**
     * Compute FFT, locate dominant spectral peak, and perform harmonic & sub-harmonic checking.
     */
    private fun estimateBpmWithHarmonics(signal: DoubleArray, fps: Double): SpectralAnalysisResult {
        val n = max(512, nextPowerOf2(signal.size))
        val padded = DoubleArray(n)

        // Apply Hann window
        for (i in signal.indices) {
            val hann = 0.5 * (1.0 - cos(2.0 * PI * i / (signal.size - 1)))
            padded[i] = signal[i] * hann
        }

        val fft = realFft(padded)
        val freqResolution = fps / n

        // Collect all local peaks within physiological passband [0.75, 2.50] Hz
        data class Peak(val bin: Int, val freqHz: Double, val power: Double)
        val peaks = mutableListOf<Peak>()
        var bandPower = 0.0
        var totalPower = 0.0

        for (k in 1 until n / 2) {
            val freq = k * freqResolution
            val power = fft[k]
            totalPower += power

            if (freq in LOW_PASS_HZ..EXTENDED_HIGH_PASS_HZ) {
                bandPower += power
                // Check if local peak
                if (k > 1 && k < n / 2 - 1) {
                    if (power > fft[k - 1] && power > fft[k + 1] && power > 1e-9) {
                        // 3-point parabolic interpolation for fractional bin refinement
                        val pL = fft[k - 1]
                        val pC = fft[k]
                        val pR = fft[k + 1]
                        val denom = 2.0 * (2.0 * pC - pL - pR).coerceAtLeast(1e-12)
                        val delta = (pR - pL) / denom
                        val refinedFreq = (k + delta) * freqResolution
                        peaks.add(Peak(k, refinedFreq, pC))
                    }
                }
            }
        }

        if (peaks.isEmpty() || bandPower <= 0.0) {
            return SpectralAnalysisResult(0.0, 0.0, 0.0, false, 0.0, 0.0)
        }

        // Global maximum peak
        val maxPeak = peaks.maxByOrNull { it.power } ?: peaks.first()
        var selectedFreq = maxPeak.freqHz
        var isHarmonicCorrected = false

        // ── Harmonic Doubling Investigation (The 169 BPM Fix) ────────
        // If the strongest peak is above ~1.5 Hz (>90 BPM), check if there is a
        // sub-harmonic fundamental around f_max / 2 (e.g. 2.8 Hz -> 1.4 Hz).
        if (maxPeak.freqHz >= 1.50) {
            val targetSubFreq = maxPeak.freqHz / 2.0
            var bestSubFreq = 0.0
            var bestSubPower = 0.0

            val peakCandidate = peaks.filter {
                it.freqHz in (targetSubFreq * 0.85)..(targetSubFreq * 1.15) && it.freqHz >= LOW_PASS_HZ
            }.maxByOrNull { it.power }

            if (peakCandidate != null) {
                bestSubFreq = peakCandidate.freqHz
                bestSubPower = peakCandidate.power
            } else {
                val minBin = max(1, (targetSubFreq * 0.85 / freqResolution).toInt())
                val maxBin = min(n / 2 - 1, (targetSubFreq * 1.15 / freqResolution).toInt())
                for (k in minBin..maxBin) {
                    if (fft[k] > bestSubPower) {
                        bestSubPower = fft[k]
                        bestSubFreq = k * freqResolution
                    }
                }
            }

            if (bestSubPower >= SUBHARMONIC_POWER_RATIO * maxPeak.power && bestSubFreq >= LOW_PASS_HZ) {
                selectedFreq = bestSubFreq
                isHarmonicCorrected = true
                safeLogI(TAG, "[HarmonicFix] 2nd Harmonic detected at ${"%.2f".format(maxPeak.freqHz)}Hz (${(maxPeak.freqHz*60).toInt()} BPM). Fundamental corrected to ${"%.2f".format(selectedFreq)}Hz (${(selectedFreq*60).toInt()} BPM)")
            }
        }

        // Check for 3rd harmonic if peak is very high (>2.25 Hz, >135 BPM)
        if (!isHarmonicCorrected && maxPeak.freqHz >= 2.25) {
            val targetSub3Freq = maxPeak.freqHz / 3.0
            val sub3Candidate = peaks.filter {
                it.freqHz in (targetSub3Freq * 0.85)..(targetSub3Freq * 1.15) && it.freqHz >= LOW_PASS_HZ
            }.maxByOrNull { it.power }

            if (sub3Candidate != null && sub3Candidate.power / maxPeak.power >= 0.25) {
                selectedFreq = sub3Candidate.freqHz
                isHarmonicCorrected = true
                safeLogI(TAG, "[HarmonicFix] 3rd Harmonic detected at ${"%.2f".format(maxPeak.freqHz)}Hz. Fundamental corrected to ${"%.2f".format(selectedFreq)}Hz")
            }
        }

        val bpm = (selectedFreq * 60.0).coerceIn(40.0, 200.0)

        // Spectral concentration (confidence) around selected fundamental
        val selectedBin = (selectedFreq / freqResolution).roundToInt()
        var peakBandPower = 0.0
        for (k in max(1, selectedBin - 2)..min(n / 2 - 1, selectedBin + 2)) {
            peakBandPower += fft[k]
        }
        val confidence = (peakBandPower / bandPower.coerceAtLeast(1e-12)).coerceIn(0.0, 1.0)

        // SNR calculation matching rPPG-Toolbox post_process.py
        val remainderPower = (bandPower - peakBandPower).coerceAtLeast(1e-12)
        val snrDb = 10.0 * log10((peakBandPower / remainderPower).coerceAtLeast(1e-6))

        return SpectralAnalysisResult(
            maxPeakFreqHz = maxPeak.freqHz,
            selectedFreqHz = selectedFreq,
            selectedBpm = bpm,
            isHarmonicCorrected = isHarmonicCorrected,
            confidence = confidence,
            snrDb = snrDb
        )
    }

    private fun realFft(x: DoubleArray): DoubleArray {
        val n = x.size
        val re = x.copyOf()
        val im = DoubleArray(n)

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }

        var len = 2
        while (len <= n) {
            val ang = 2.0 * PI / len
            val wRe = cos(ang)
            val wIm = -sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0; var curIm = 0.0
                for (jj in 0 until len / 2) {
                    val uRe = re[i + jj]
                    val uIm = im[i + jj]
                    val vRe = re[i + jj + len / 2] * curRe - im[i + jj + len / 2] * curIm
                    val vIm = re[i + jj + len / 2] * curIm + im[i + jj + len / 2] * curRe
                    re[i + jj] = uRe + vRe
                    im[i + jj] = uIm + vIm
                    re[i + jj + len / 2] = uRe - vRe
                    im[i + jj + len / 2] = uIm - vIm
                    val tmpRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = tmpRe
                }
                i += len
            }
            len *= 2
        }

        return DoubleArray(n / 2) { k -> re[k] * re[k] + im[k] * im[k] }
    }

    private fun updateSmoothedBpm(rawBpm: Double, confidence: Double): Double {
        if (rawBpm < 40.0 || rawBpm > 200.0 || confidence < 0.05) {
            return smoothedBpm.coerceAtLeast(0.0)
        }

        // Outlier rejection: check against recent median
        if (recentBpmBuffer.size >= 3) {
            val medianBpm = recentBpmBuffer.sorted()[recentBpmBuffer.size / 2]
            if (abs(rawBpm - medianBpm) > BPM_OUTLIER_THRESHOLD && confidence < 0.45) {
                return smoothedBpm
            }
        }

        recentBpmBuffer.addLast(rawBpm)
        if (recentBpmBuffer.size > MEDIAN_WINDOW) recentBpmBuffer.removeFirst()

        val alpha = (0.25 * (0.5 + confidence)).coerceIn(0.08, 0.45)
        smoothedBpm = if (smoothedBpm < 30.0) {
            rawBpm
        } else {
            smoothedBpm * (1 - alpha) + rawBpm * alpha
        }

        return smoothedBpm
    }

    private fun classifyQuality(filtered: DoubleArray, confidence: Double, snrDb: Double): SignalQuality {
        if (confidence < 0.05) return SignalQuality.NO_SIGNAL
        return when {
            confidence >= 0.30 || snrDb >= 2.0 -> SignalQuality.GOOD
            confidence >= 0.12 || snrDb >= -1.0 -> SignalQuality.FAIR
            confidence >= 0.05 -> SignalQuality.POOR
            else -> SignalQuality.NO_SIGNAL
        }
    }

    private fun nextPowerOf2(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }
}

fun DoubleArray.std(): Double {
    if (size < 2) return 0.0
    val mean = average()
    return sqrt(sumOf { (it - mean).pow(2) } / (size - 1))
}

fun DoubleArray.variance(): Double {
    if (size < 2) return 0.0
    val mean = average()
    return sumOf { (it - mean).pow(2) } / (size - 1)
}

private fun safeLogD(tag: String, msg: String) {
    try {
        Log.d(tag, msg)
    } catch (_: Throwable) {
        println("[$tag] $msg")
    }
}

private fun safeLogI(tag: String, msg: String) {
    try {
        Log.i(tag, msg)
    } catch (_: Throwable) {
        println("[$tag] $msg")
    }
}
