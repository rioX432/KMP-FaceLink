package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.voice.lipsync.internal.VisemeMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class VisemeMapperTest {

    @Test
    fun emptyStringMapToSilence() {
        assertEquals(Viseme.SIL, VisemeMapper.map(""))
    }

    @Test
    fun silMapsToSilence() {
        assertEquals(Viseme.SIL, VisemeMapper.map("sil"))
    }

    @Test
    fun bilabialsMaptoPP() {
        assertEquals(Viseme.PP, VisemeMapper.map("p"))
        assertEquals(Viseme.PP, VisemeMapper.map("b"))
        assertEquals(Viseme.PP, VisemeMapper.map("m"))
    }

    @Test
    fun labiodentalsMapToFF() {
        assertEquals(Viseme.FF, VisemeMapper.map("f"))
        assertEquals(Viseme.FF, VisemeMapper.map("v"))
    }

    @Test
    fun alveolarsMapToDD() {
        assertEquals(Viseme.DD, VisemeMapper.map("t"))
        assertEquals(Viseme.DD, VisemeMapper.map("d"))
        assertEquals(Viseme.DD, VisemeMapper.map("n"))
        assertEquals(Viseme.DD, VisemeMapper.map("l"))
    }

    @Test
    fun velarsMapToKK() {
        assertEquals(Viseme.KK, VisemeMapper.map("k"))
        assertEquals(Viseme.KK, VisemeMapper.map("g"))
    }

    @Test
    fun fricativesMapToSS() {
        assertEquals(Viseme.SS, VisemeMapper.map("s"))
        assertEquals(Viseme.SS, VisemeMapper.map("z"))
    }

    @Test
    fun vowelAMapsToAA() {
        assertEquals(Viseme.AA, VisemeMapper.map("a"))
        assertEquals(Viseme.AA, VisemeMapper.map("A")) // VOICEVOX
    }

    @Test
    fun vowelEMapsToE() {
        assertEquals(Viseme.E, VisemeMapper.map("e"))
        assertEquals(Viseme.E, VisemeMapper.map("E")) // VOICEVOX
    }

    @Test
    fun vowelIMapsToIH() {
        assertEquals(Viseme.IH, VisemeMapper.map("i"))
        assertEquals(Viseme.IH, VisemeMapper.map("I")) // VOICEVOX
    }

    @Test
    fun vowelOMapsToOH() {
        assertEquals(Viseme.OH, VisemeMapper.map("o"))
        assertEquals(Viseme.OH, VisemeMapper.map("O")) // VOICEVOX
    }

    @Test
    fun vowelUMapsToOU() {
        assertEquals(Viseme.OU, VisemeMapper.map("u"))
        assertEquals(Viseme.OU, VisemeMapper.map("U")) // VOICEVOX
    }

    @Test
    fun unknownPhonemeMapToSilence() {
        assertEquals(Viseme.SIL, VisemeMapper.map("xyz"))
        assertEquals(Viseme.SIL, VisemeMapper.map("???"))
    }

    @Test
    fun whitespaceIsTrimmed() {
        assertEquals(Viseme.AA, VisemeMapper.map(" a "))
        assertEquals(Viseme.PP, VisemeMapper.map("  p  "))
    }

    @Test
    fun voicevoxPauseMapsToSilence() {
        assertEquals(Viseme.SIL, VisemeMapper.map("pau"))
    }
}
