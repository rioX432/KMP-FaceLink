package io.github.kmpfacelink.capture

/**
 * Opens a [RawFrameSink] writing [RawFrameFormat.FRAMES_FILE] and
 * [RawFrameFormat.FRAMES_META_FILE] inside [directory].
 *
 * [width] and [height] are fixed for the whole take: the stream is a flat concatenation with no
 * per-frame header, so the reader needs the geometry once and every frame must match it.
 */
public expect fun openRawFrameSink(directory: String, width: Int, height: Int): RawFrameSink
