package io.github.kmpfacelink.voice.audio

/**
 * Creates a platform-specific [AudioRecorder] instance.
 *
 * On Android, uses [android.media.AudioRecord].
 * On iOS, uses [AVAudioEngine].
 */
public expect fun createAudioRecorder(): AudioRecorder

/**
 * Creates a platform-specific [AudioPlayer] instance.
 *
 * On Android, uses [android.media.AudioTrack].
 * On iOS, uses [AVAudioPlayer].
 */
public expect fun createAudioPlayer(): AudioPlayer
