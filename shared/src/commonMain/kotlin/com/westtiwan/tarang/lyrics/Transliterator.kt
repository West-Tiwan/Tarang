package com.westtiwan.tarang.lyrics

import com.westtiwan.tarang.platform.anyToLatin
import com.westtiwan.tarang.platform.japaneseReadings

enum class Script(val lang: String?) {
    LATIN(null), DEVANAGARI("hi"), GURMUKHI("pa"), ARABIC(null), JAPANESE("ja"), CHINESE("zh"), HANGUL("ko"),
    CYRILLIC(null), GREEK("el"), THAI("th"), HEBREW("he"), TAMIL("ta"), TELUGU("te"), BENGALI("bn"), OTHER(null),
}

fun detectScript(text: String): Script {
    val counts = IntArray(Script.entries.size)
    var kana = 0
    for (ch in text) {
        val c = ch.code
        if (c in 0x3040..0x30FF || c in 0x31F0..0x31FF || c in 0xFF66..0xFF9F) kana++
        val s = when {
            c in 0x0900..0x097F -> Script.DEVANAGARI
            c in 0x0A00..0x0A7F -> Script.GURMUKHI
            c in 0x0980..0x09FF -> Script.BENGALI
            c in 0x0B80..0x0BFF -> Script.TAMIL
            c in 0x0C00..0x0C7F -> Script.TELUGU
            c in 0x0600..0x06FF || c in 0x0750..0x077F || c in 0xFB50..0xFEFF -> Script.ARABIC
            c in 0x0590..0x05FF -> Script.HEBREW
            c in 0x0E00..0x0E7F -> Script.THAI
            c in 0x3040..0x30FF || c in 0x31F0..0x31FF || c in 0xFF66..0xFF9F -> Script.JAPANESE
            c in 0x4E00..0x9FFF || c in 0x3400..0x4DBF -> Script.CHINESE
            c in 0xAC00..0xD7AF || c in 0x1100..0x11FF || c in 0x3130..0x318F -> Script.HANGUL
            c in 0x0400..0x04FF -> Script.CYRILLIC
            c in 0x0370..0x03FF -> Script.GREEK
            ch.isLetter() && c < 0x0250 -> Script.LATIN
            ch.isLetter() -> Script.OTHER
            else -> null
        }
        if (s != null) counts[s.ordinal]++
    }
    // Japanese mixes kanji with kana; any meaningful amount of kana means the Han characters are Japanese.
    if (kana > 0 && kana * 10 >= counts[Script.CHINESE.ordinal]) {
        counts[Script.JAPANESE.ordinal] += counts[Script.CHINESE.ordinal]
        counts[Script.CHINESE.ordinal] = 0
    }
    val best = counts.indices.maxByOrNull { counts[it] } ?: return Script.LATIN
    return if (counts[best] == 0) Script.LATIN else Script.entries[best]
}

private val hinglishWords = setOf(
    "hai", "hain", "ho", "hoon", "hu", "main", "mein", "mera", "meri", "mere", "tera", "teri", "tere", "tu", "tum", "tujhe",
    "mujhe", "mujhko", "dil", "pyaar", "pyar", "ishq", "nahi", "nahin", "na", "kya", "kyun", "kyon", "kaise", "jaise", "yeh", "ye",
    "woh", "wo", "hum", "humko", "aur", "bhi", "ki", "ka", "ke", "ko", "se", "pe", "par", "ab", "jab", "tab", "kabhi", "sab", "zindagi",
    "aankhon", "aankhein", "raat", "din", "yaar", "yaara", "jaana", "jaan", "sanam", "saath", "baat", "baatein", "chal", "chalo",
    "dekho", "dekh", "sun", "suno", "tujhko", "apna", "apni", "apne", "koi", "kuch", "bas", "tha", "thi", "the", "gaya", "gayi",
    "raha", "rahi", "rahe", "hoga", "hogi", "kar", "karo", "karna", "le", "lo", "de", "do", "aaja", "aa", "ja", "mohabbat", "khuda",
)

/** Rough check for Hindi or Punjabi written in Latin script. */
fun looksLikeHinglish(text: String): Boolean {
    val words = text.lowercase().split(Regex("[^a-z]+")).filter { it.isNotBlank() }
    if (words.size < 4) return false
    val hits = words.count { it in hinglishWords }
    return hits.toDouble() / words.size > 0.18
}

/**
 * Converts Devanagari and Gurmukhi into the casual romanization people use in Hinglish lyrics,
 * for example "दिल धड़कने लगा" becomes "dil dhadakne laga".
 */
object Hinglish {
    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n", 'ऩ' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ऱ' to "r", 'ल' to "l", 'ळ' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        '\u0958' to "q", '\u0959' to "kh", '\u095A' to "gh", '\u095B' to "z", '\u095C' to "d", '\u095D' to "rh", '\u095E' to "f", '\u095F' to "y",
    )
    private val nuktaForms = mapOf(
        'क' to '\u0958', 'ख' to '\u0959', 'ग' to '\u095A', 'ज' to '\u095B', 'ड' to '\u095C', 'ढ' to '\u095D', 'फ' to '\u095E', 'य' to '\u095F',
    )
    private val independentVowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o", 'ऍ' to "e",
    )
    private val matras = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ॅ' to "e",
    )
    private const val VIRAMA = '्'
    private const val NUKTA = '़'
    private const val ANUSVARA = 'ं'
    private const val CHANDRABINDU = 'ँ'
    private const val VISARGA = 'ः'

    private sealed interface Unit
    private data class Syl(val cons: String, var vowel: String?, val explicit: Boolean, var nasal: Boolean = false, val consKey: Char? = null) : Unit
    private data class Raw(val text: String) : Unit

    /** Pronunciation in plain English letters. [docScript] is the script of the whole song, which matters for lines of pure kanji. */
    fun romanize(text: String, docScript: Script = detectScript(text)): String {
        if (text.isBlank()) return text
        return when (docScript) {
            Script.DEVANAGARI -> devanagariToHinglish(text)
            Script.GURMUKHI -> devanagariToHinglish(gurmukhiToDevanagari(text))
            Script.JAPANESE -> japaneseToRomaji(text)
            Script.LATIN -> text
            else -> anyToLatin(text)
        }
    }

    private fun japaneseToRomaji(text: String): String {
        val tokens = japaneseReadings(text) ?: return Kana.toRomaji(text)
        val sb = StringBuilder()
        for (t in tokens) {
            val roman = when {
                t.particle && t.surface == "は" -> "wa"
                t.particle && t.surface == "へ" -> "e"
                t.particle && t.surface == "を" -> "o"
                else -> Kana.toRomaji(t.reading ?: t.surface)
            }
            val punctuation = roman.isNotEmpty() && roman.none { it.isLetterOrDigit() }
            if (sb.isNotEmpty() && !t.attach && !punctuation && !sb.last().isWhitespace()) sb.append(' ')
            sb.append(roman)
        }
        return sb.toString().replace(Regex(" +"), " ").trim()
    }

    /** Gurmukhi mirrors the Devanagari block layout 0x100 lower, apart from a few signs handled here. */
    fun gurmukhiToDevanagari(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            val c = ch.code
            when {
                ch == 'ੱ' -> {
                    // Addak doubles the next consonant.
                    val next = text.getOrNull(i + 1)
                    if (next != null && next.code in 0x0A15..0x0A39) {
                        sb.append((next.code - 0x100).toChar()).append(VIRAMA)
                    }
                }
                ch == 'ੰ' || ch == 'ਂ' -> sb.append(ANUSVARA)
                ch == 'ੲ' -> sb.append('इ')
                ch == 'ੳ' -> sb.append('उ')
                ch == '\u0A59' -> sb.append('ख').append(NUKTA)
                ch == '\u0A5A' -> sb.append('ग').append(NUKTA)
                ch == '\u0A5B' -> sb.append('ज').append(NUKTA)
                ch == 'ੜ' -> sb.append('ड').append(NUKTA)
                ch == '\u0A5E' -> sb.append('फ').append(NUKTA)
                ch == '\u0A33' -> sb.append('ल')
                ch == '\u0A36' -> sb.append('श')
                c in 0x0A01..0x0A6F -> sb.append((c - 0x100).toChar())
                else -> sb.append(ch)
            }
            i++
        }
        return sb.toString()
    }

    fun devanagariToHinglish(text: String): String {
        val out = StringBuilder()
        val word = StringBuilder()
        fun flush() {
            if (word.isNotEmpty()) {
                out.append(romanizeWord(word.toString()))
                word.clear()
            }
        }
        for (ch in text) {
            if (ch.code in 0x0900..0x097F && ch != '।' && ch != '॥' && ch.code !in 0x0966..0x096F) word.append(ch)
            else {
                flush()
                out.append(
                    when (ch) {
                        '।', '॥' -> "."
                        in '०'..'९' -> ('0' + (ch - '०')).toString()
                        else -> ch.toString()
                    },
                )
            }
        }
        flush()
        return out.toString()
    }

    private fun romanizeWord(word: String): String {
        val units = ArrayList<Unit>()
        var i = 0
        while (i < word.length) {
            var ch = word[i]
            if (i + 1 < word.length && word[i + 1] == NUKTA && nuktaForms.containsKey(ch)) {
                ch = nuktaForms.getValue(ch)
                i++
            }
            when {
                consonants.containsKey(ch) -> {
                    val next = word.getOrNull(i + 1)
                    when {
                        next == VIRAMA -> {
                            units += Syl(consonants.getValue(ch), null, true, consKey = ch)
                            i++
                        }
                        next != null && matras.containsKey(next) -> {
                            units += Syl(consonants.getValue(ch), matras.getValue(next), true, consKey = ch)
                            i++
                        }
                        else -> units += Syl(consonants.getValue(ch), "a", false, consKey = ch)
                    }
                }
                independentVowels.containsKey(ch) -> units += Syl("", independentVowels.getValue(ch), true)
                ch == ANUSVARA || ch == CHANDRABINDU -> (units.lastOrNull() as? Syl)?.nasal = true
                ch == VISARGA -> units += Raw("h")
                matras.containsKey(ch) -> units += Raw(matras.getValue(ch))
                else -> units += Raw(ch.toString())
            }
            i++
        }
        val syls = units.filterIsInstance<Syl>()
        // Schwa deletion, right to left: the final inherent vowel goes, then any inherent vowel
        // between two pronounced vowels when the next consonant keeps its vowel.
        if (syls.size > 1) {
            val last = syls.last()
            if (!last.explicit && last.vowel == "a") last.vowel = null
            for (k in syls.size - 2 downTo 1) {
                val s = syls[k]
                if (s.explicit || s.vowel != "a" || s.cons.isEmpty()) continue
                val prev = syls[k - 1]
                val next = syls[k + 1]
                if (prev.vowel != null && next.vowel != null && next.cons.isNotEmpty()) s.vowel = null
            }
        }
        val sb = StringBuilder()
        units.forEachIndexed { idx, u ->
            when (u) {
                is Raw -> sb.append(u.text)
                is Syl -> {
                    sb.append(u.cons)
                    val isLast = units.subList(idx + 1, units.size).none { it is Syl }
                    var v = u.vowel ?: ""
                    if (isLast) {
                        v = when (v) { "aa" -> "a"; "ee" -> "i"; "oo" -> "u"; else -> v }
                    }
                    sb.append(v)
                    if (u.nasal) {
                        val nextCons = (units.drop(idx + 1).firstOrNull { it is Syl } as? Syl)?.cons
                        sb.append(if (nextCons != null && (nextCons.startsWith("p") || nextCons.startsWith("b") || nextCons.startsWith("m"))) "m" else "n")
                    }
                }
            }
        }
        return sb.toString()
    }

    private val reverseCons = listOf(
        "chh" to "छ", "kh" to "ख", "gh" to "घ", "ch" to "च", "jh" to "झ", "th" to "थ", "dh" to "ध", "ph" to "फ",
        "bh" to "भ", "sh" to "श", "k" to "क", "g" to "ग", "j" to "ज", "t" to "त", "d" to "द", "n" to "न", "p" to "प",
        "f" to "फ़", "b" to "ब", "m" to "म", "y" to "य", "r" to "र", "l" to "ल", "v" to "व", "w" to "व", "s" to "स",
        "h" to "ह", "z" to "ज़", "q" to "क़", "x" to "क्स", "c" to "क",
    )
    private val reverseVowels = listOf(
        "aa" to ("आ" to "ा"), "ai" to ("ऐ" to "ै"), "au" to ("औ" to "ौ"), "ee" to ("ई" to "ी"), "ii" to ("ई" to "ी"),
        "oo" to ("ऊ" to "ू"), "uu" to ("ऊ" to "ू"), "a" to ("अ" to ""), "i" to ("इ" to "ि"), "u" to ("उ" to "ु"),
        "e" to ("ए" to "े"), "o" to ("ओ" to "ो"),
    )

    /** Best effort conversion of Hinglish back into Devanagari, used so romanized Hindi lyrics can be translated. */
    fun hinglishToDevanagari(text: String): String =
        Regex("[A-Za-z]+").replace(text) { m -> wordToDevanagari(m.value.lowercase()) }

    private fun wordToDevanagari(w: String): String {
        val sb = StringBuilder()
        var i = 0
        var lastWasConsonant = false
        while (i < w.length) {
            val vowel = reverseVowels.firstOrNull { w.startsWith(it.first, i) }
            if (vowel != null) {
                val atEnd = i + vowel.first.length == w.length
                val (independent, matra) = vowel.second
                if (lastWasConsonant) {
                    sb.append(
                        when {
                            vowel.first == "a" && atEnd && w.length > 2 -> "ा"
                            vowel.first == "i" && atEnd -> "ी"
                            else -> matra
                        },
                    )
                } else {
                    sb.append(independent)
                }
                i += vowel.first.length
                lastWasConsonant = false
                continue
            }
            if (w[i] == 'n' && i == w.length - 1 && i > 0 && !lastWasConsonant && w[i - 1] != 'a') {
                sb.append("ं")
                i++
                continue
            }
            val cons = reverseCons.firstOrNull { w.startsWith(it.first, i) }
            if (cons != null) {
                if (lastWasConsonant) sb.append("्")
                sb.append(cons.second)
                i += cons.first.length
                lastWasConsonant = true
                continue
            }
            sb.append(w[i])
            i++
            lastWasConsonant = false
        }
        return sb.toString()
    }
}
