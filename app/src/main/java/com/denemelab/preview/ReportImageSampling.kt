package com.denemelab.preview

/**
 * A tall, narrow report must not lose legibility just because its HEIGHT
 * exceeds 4,800px. Decode at native resolution until the actual memory
 * budget (pixel count) is exceeded. BitmapFactory uses powers of two.
 */
object ReportImageSampling {
    private const val MAX_DECODE_PIXELS = 20_000_000L

    fun sampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (sample < 1024 &&
            ((width.toLong() + sample - 1) / sample) *
            ((height.toLong() + sample - 1) / sample) > MAX_DECODE_PIXELS) {
            sample *= 2
        }
        return sample
    }
}
