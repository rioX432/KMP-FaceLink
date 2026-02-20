package io.github.kmpfacelink.voice.audio

import io.github.kmpfacelink.voice.audio.internal.PlatformAudioPlayer
import io.github.kmpfacelink.voice.audio.internal.PlatformAudioRecorder

public actual fun createAudioRecorder(): AudioRecorder = PlatformAudioRecorder()

public actual fun createAudioPlayer(): AudioPlayer = PlatformAudioPlayer()
