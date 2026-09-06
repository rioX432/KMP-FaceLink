package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertFailsWith

class CaptureStorageProfileTest {
    @Test
    fun `canonical cap accepts frame 873 and rejects frame 874`() {
        val receipt = CaptureStorageReceipt.coefficientsOnly(0, 0)
        receipt.requireNextCanonicalFrame(872)
        assertFailsWith<IllegalArgumentException> { receipt.requireNextCanonicalFrame(873) }
    }

    @Test
    fun `record cap accepts 2048th record and rejects 2049th`() {
        val receipt = CaptureStorageReceipt.coefficientsOnly(0, 0)
        receipt.requireNextRecord(2_047)
        assertFailsWith<IllegalArgumentException> { receipt.requireNextRecord(2_048) }
    }

    @Test
    fun `coefficient only receipt rejects stored pixels`() {
        assertFailsWith<IllegalArgumentException> {
            CaptureStorageReceipt.coefficientsOnly(1440, 1080).requireSchema4Consistency(1)
        }
    }

    @Test
    fun `canonical receipt rejects native sized storage`() {
        val receipt = CaptureStorageReceipt(
            CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
            CapturePixelSemantics.CANONICALIZED_IOS_CAPTURE_COPY,
            1440, 1080, 1440, 1080, "UPRIGHT", "NOT_MIRRORED", "test",
        )
        assertFailsWith<IllegalArgumentException> { receipt.requireSchema4Consistency(1) }
    }

    @Test
    fun `Android exact replay rejects iOS canonical semantics`() {
        val receipt = CaptureStorageReceipt(
            CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
            CapturePixelSemantics.CANONICALIZED_IOS_CAPTURE_COPY,
            1440, 1080,
            CaptureStorageReceipt.CANONICAL_WIDTH,
            CaptureStorageReceipt.CANONICAL_HEIGHT,
            "UPRIGHT", "NOT_MIRRORED", "ios-ci-v1",
        )
        assertFailsWith<IllegalArgumentException> { receipt.requireExactAndroidReplay() }
    }
}
