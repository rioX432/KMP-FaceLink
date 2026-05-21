package io.github.kmpfacelink.interact

import io.github.kmpfacelink.interact.model.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

/**
 * Merges events from multiple [StreamEventSource]s into a single [Flow].
 *
 * Useful when a VTuber streams to multiple platforms simultaneously and
 * wants to react to all audience interactions in a unified way.
 */
public class StreamEventAggregator {
    private val _sources: MutableList<StreamEventSource> = mutableListOf()

    /** Combined event flow from all registered sources. */
    public val events: Flow<StreamEvent>
        get() = _sources.map { it.events }.merge()

    /** Adds a source to the aggregator. */
    public fun addSource(source: StreamEventSource) {
        _sources.add(source)
    }

    /** Removes a previously added source. */
    public fun removeSource(source: StreamEventSource) {
        _sources.remove(source)
    }

    /** Connects all registered sources. */
    public suspend fun connectAll() {
        for (source in _sources) {
            source.connect()
        }
    }

    /** Disconnects all registered sources. */
    public suspend fun disconnectAll() {
        for (source in _sources) {
            source.disconnect()
        }
    }
}
