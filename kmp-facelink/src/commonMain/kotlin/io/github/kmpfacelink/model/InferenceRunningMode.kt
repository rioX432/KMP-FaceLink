package io.github.kmpfacelink.model

/**
 * How the tracking model treats the stream of frames it is given.
 *
 * This is not only a performance knob: MediaPipe's face landmarker reuses the previous frame's
 * face rectangle in the two streaming modes and skips the face detector entirely while tracking
 * holds ([face_landmarker_graph.cc](https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/tasks/cc/vision/face_landmarker/face_landmarker_graph.cc):
 * *"While in stream mode, skip face detector graph when we successfully track the faces from the
 * last frame."*). [IMAGE] detects on every frame. **The same picture can therefore produce
 * different landmarks under different modes**, which makes this the difference between two
 * measurements that look comparable and two that are not.
 *
 * Android (MediaPipe): all three are supported.
 * iOS (ARKit): no effect — ARKit owns its own session cadence.
 * Wasm/Web: [IMAGE] and [VIDEO] map to the MediaPipe JS equivalents.
 */
public enum class InferenceRunningMode {
    /**
     * Treat every frame as unrelated. The face detector runs each time, so results do not depend
     * on what came before. Slowest, and the only mode that is reproducible frame-by-frame.
     */
    IMAGE,

    /**
     * A sequence of frames with monotonically increasing timestamps, processed synchronously.
     * Tracks across frames and skips detection while tracking holds.
     */
    VIDEO,

    /**
     * A live camera feed, processed asynchronously through a result callback. Tracks across frames
     * like [VIDEO] and additionally drops frames to keep up. This is the product configuration.
     */
    LIVE_STREAM,
}
