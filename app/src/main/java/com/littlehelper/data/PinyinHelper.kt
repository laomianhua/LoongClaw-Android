package com.littlehelper.data

import net.sourceforge.pinyin4j.PinyinHelper as Pinyin4j
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/** 人名/地名拼音，用于同音字检索（涵 han ≠ 杭/航 hang）。 */
object PinyinHelper {
    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    /** 无空格小写拼音，如「夏子杭」→「xiazihang」。 */
    fun toPinyinKey(text: String): String {
        if (text.isBlank()) return ""
        return buildString {
            text.forEach { char ->
                if (char.code in 0x4E00..0x9FFF) {
                    val syllables = Pinyin4j.toHanyuPinyinStringArray(char, format)
                    append(syllables?.firstOrNull()?.filter { it.isLetter() }.orEmpty())
                } else if (char.isLetterOrDigit()) {
                    append(char.lowercaseChar())
                }
            }
        }
    }

    fun samePinyin(a: String, b: String): Boolean {
        val pa = toPinyinKey(a)
        val pb = toPinyinKey(b)
        return pa.isNotEmpty() && pa == pb
    }

    fun appendPinyinSearchTerms(text: String): String {
        val key = toPinyinKey(text)
        return if (key.isBlank()) text else "$text $key"
    }
}
