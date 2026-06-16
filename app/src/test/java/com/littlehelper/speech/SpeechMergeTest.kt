package com.littlehelper.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechMergeTest {

    @Test
    fun merge_emptyBase_returnsSegment() {
        assertEquals("9.9元那种", mergeSpeechSegments("", "9.9元那种"))
    }

    @Test
    fun merge_segmentExtendsBase_returnsLongerSegment() {
        val base = "我今天下午2点和朋友去清河万象城喝咖啡"
        val segment = "我今天下午2点和朋友去清河万象城喝咖啡9.9元那种"
        assertEquals(segment, mergeSpeechSegments(base, segment))
    }

    @Test
    fun merge_appendTail_whenNoOverlap() {
        val base = "我今天下午2点和朋友去清河万象城喝咖啡"
        val tail = "9.9元那种"
        assertEquals(base + tail, mergeSpeechSegments(base, tail))
    }

    @Test
    fun merge_overlapSuffixPrefix() {
        assertEquals("abcdef", mergeSpeechSegments("abc", "cdef"))
    }

    @Test
    fun merge_coffeeAndLuckinTail() {
        val first = "我今天下午2点去清河万象城喝咖啡"
        val tail = "9.9元瑞幸"
        assertEquals(first + tail, mergeSpeechSegments(first, tail))
    }
}
