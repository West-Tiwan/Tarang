package com.westtiwan.tarang.lyrics

/** Hepburn romanization of hiragana and katakana. Other characters pass through. */
object Kana {
    private val digraphs = mapOf(
        "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo", "シャ" to "sha", "シュ" to "shu", "ショ" to "sho", "シェ" to "she",
        "チャ" to "cha", "チュ" to "chu", "チョ" to "cho", "チェ" to "che", "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
        "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo", "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
        "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo", "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
        "ジャ" to "ja", "ジュ" to "ju", "ジョ" to "jo", "ジェ" to "je", "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
        "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo", "ヂャ" to "ja", "ヂュ" to "ju", "ヂョ" to "jo",
        "ファ" to "fa", "フィ" to "fi", "フェ" to "fe", "フォ" to "fo", "ティ" to "ti", "ディ" to "di", "トゥ" to "tu", "ドゥ" to "du",
        "ウィ" to "wi", "ウェ" to "we", "ウォ" to "wo", "ヴァ" to "va", "ヴィ" to "vi", "ヴェ" to "ve", "ヴォ" to "vo", "ツァ" to "tsa",
    )
    private val mono = mapOf(
        'ア' to "a", 'イ' to "i", 'ウ' to "u", 'エ' to "e", 'オ' to "o",
        'カ' to "ka", 'キ' to "ki", 'ク' to "ku", 'ケ' to "ke", 'コ' to "ko",
        'サ' to "sa", 'シ' to "shi", 'ス' to "su", 'セ' to "se", 'ソ' to "so",
        'タ' to "ta", 'チ' to "chi", 'ツ' to "tsu", 'テ' to "te", 'ト' to "to",
        'ナ' to "na", 'ニ' to "ni", 'ヌ' to "nu", 'ネ' to "ne", 'ノ' to "no",
        'ハ' to "ha", 'ヒ' to "hi", 'フ' to "fu", 'ヘ' to "he", 'ホ' to "ho",
        'マ' to "ma", 'ミ' to "mi", 'ム' to "mu", 'メ' to "me", 'モ' to "mo",
        'ヤ' to "ya", 'ユ' to "yu", 'ヨ' to "yo",
        'ラ' to "ra", 'リ' to "ri", 'ル' to "ru", 'レ' to "re", 'ロ' to "ro",
        'ワ' to "wa", 'ヰ' to "i", 'ヱ' to "e", 'ヲ' to "o", 'ン' to "n",
        'ガ' to "ga", 'ギ' to "gi", 'グ' to "gu", 'ゲ' to "ge", 'ゴ' to "go",
        'ザ' to "za", 'ジ' to "ji", 'ズ' to "zu", 'ゼ' to "ze", 'ゾ' to "zo",
        'ダ' to "da", 'ヂ' to "ji", 'ヅ' to "zu", 'デ' to "de", 'ド' to "do",
        'バ' to "ba", 'ビ' to "bi", 'ブ' to "bu", 'ベ' to "be", 'ボ' to "bo",
        'パ' to "pa", 'ピ' to "pi", 'プ' to "pu", 'ペ' to "pe", 'ポ' to "po",
        'ヴ' to "vu", 'ァ' to "a", 'ィ' to "i", 'ゥ' to "u", 'ェ' to "e", 'ォ' to "o",
        'ャ' to "ya", 'ュ' to "yu", 'ョ' to "yo", 'ヮ' to "wa",
        '、' to ",", '。' to ".", '「' to "\"", '」' to "\"", '！' to "!", '？' to "?", '・' to " ", '　' to " ", '〜' to "~",
    )

    private fun toKatakana(s: String) = buildString {
        for (ch in s) append(if (ch.code in 0x3041..0x3096) (ch.code + 0x60).toChar() else ch)
    }

    fun toRomaji(input: String): String {
        val s = toKatakana(input)
        val out = StringBuilder()
        var i = 0
        var geminate = false
        while (i < s.length) {
            val ch = s[i]
            if (ch == 'ッ') { geminate = true; i++; continue }
            if (ch == 'ー') {
                // Long vowel mark repeats the previous vowel.
                out.lastOrNull()?.takeIf { it in "aeiou" }?.let { out.append(it) }
                i++
                continue
            }
            val pair = if (i + 1 < s.length) digraphs[s.substring(i, i + 2)] else null
            val roman = pair ?: mono[ch]
            if (roman == null) {
                out.append(ch)
                geminate = false
                i++
                continue
            }
            if (geminate && roman.isNotEmpty() && roman[0] !in "aeiou") {
                out.append(if (roman.startsWith("ch")) 't' else roman[0])
            }
            geminate = false
            // n before a vowel or y is written n' to keep syllables readable.
            if (ch == 'ン' && i + 1 < s.length && (mono[s[i + 1]]?.firstOrNull()?.let { it in "aeiouy" } == true)) out.append("n'") else out.append(roman)
            i += if (pair != null) 2 else 1
        }
        return out.toString()
    }
}
