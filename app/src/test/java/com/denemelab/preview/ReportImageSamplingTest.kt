package com.denemelab.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportImageSamplingTest {
    @Test fun tallNarrowReportRetainsEveryOriginalPixel() {
        // Old max-dimension rule sampled 1,200 × 10,000 to 300 × 2,500.
        assertEquals(1, ReportImageSampling.sampleSize(1200, 10000))
        assertEquals(1, ReportImageSampling.sampleSize(900, 14000))
    }

    @Test fun genuineHugeImagesAreDownsampledToFitMemory() {
        assertEquals(2, ReportImageSampling.sampleSize(4000, 8000))
        assertEquals(2, ReportImageSampling.sampleSize(2000, 12000))
        assertEquals(1, ReportImageSampling.sampleSize(3000, 6000))
    }

    @Test fun invalidDimensionsAreSafe() {
        assertEquals(1, ReportImageSampling.sampleSize(0, 0))
    }
}
