package io.github.kmpfacelink.sample.ui

/**
 * MediaPipe face mesh contour index arrays for face landmark visualization.
 */
internal object FaceContours {
    val FACE_OVAL = intArrayOf(
        10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
        397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
        172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109, 10,
    )

    val LEFT_EYE = intArrayOf(
        33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246, 33,
    )

    val RIGHT_EYE = intArrayOf(
        362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398, 362,
    )

    val LEFT_EYEBROW = intArrayOf(70, 63, 105, 66, 107, 55, 65, 52, 53, 46)

    val RIGHT_EYEBROW = intArrayOf(300, 293, 334, 296, 336, 285, 295, 282, 283, 276)

    val LIPS_OUTER = intArrayOf(
        61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291,
        409, 270, 269, 267, 0, 37, 39, 40, 185, 61,
    )

    val LIPS_INNER = intArrayOf(
        78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308,
        415, 310, 311, 312, 13, 82, 81, 80, 191, 78,
    )

    val LEFT_IRIS = intArrayOf(468, 469, 470, 471, 472, 468)

    val RIGHT_IRIS = intArrayOf(473, 474, 475, 476, 477, 473)
}
