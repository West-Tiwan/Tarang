package com.westtiwan.tarang

import com.westtiwan.tarang.lyrics.Hinglish
import com.westtiwan.tarang.lyrics.Kana
import com.westtiwan.tarang.lyrics.Lrc
import com.westtiwan.tarang.lyrics.Script
import com.westtiwan.tarang.lyrics.detectScript
import com.westtiwan.tarang.lyrics.looksLikeHinglish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LyricsTest {
    @Test fun scripts() {
        assertEquals(Script.JAPANESE, detectScript("君の名は"))
        assertEquals(Script.CHINESE, detectScript("我爱你中国"))
        assertEquals(Script.HANGUL, detectScript("사랑해요"))
        assertEquals(Script.DEVANAGARI, detectScript("दिल धड़कने लगा"))
    }

    @Test fun hinglish() {
        assertEquals("dil", Hinglish.devanagariToHinglish("दिल"))
        assertEquals("dhadkan", Hinglish.devanagariToHinglish("धड़कन"))
        assertEquals("karna", Hinglish.devanagariToHinglish("करना"))
        assertEquals("pyaar", Hinglish.devanagariToHinglish("प्यार"))
        assertEquals("tera", Hinglish.devanagariToHinglish("तेरा"))
        assertTrue(looksLikeHinglish("tera mera pyaar hai yeh dil kya kare"))
    }

    @Test fun kana() {
        assertEquals("kimi no na wa", Hinglish.romanize("君の名は"))
        assertTrue(Hinglish.romanize("確かなことは、まっくらの中").contains("makkura"))
        assertTrue(Hinglish.romanize("やっと綺麗だって思った").endsWith("omotta"))
        assertTrue(Hinglish.romanize("少し大きめの苦痛を履いて").endsWith("haite"))
        assertEquals("arigatou", Kana.toRomaji("ありがとう"))
        assertEquals("kitto", Kana.toRomaji("きっと"))
        assertEquals("ko-hi-".replace("-", ""), Kana.toRomaji("こひ"))
    }

    @Test fun lrc() {
        val d = Lrc.parse("[00:01.50]hello\n[00:03.00]world")!!
        assertTrue(d.synced)
        assertEquals(1500L, d.lines[0].startMs)
        assertEquals(1, d.activeIndex(3100))
    }
}
