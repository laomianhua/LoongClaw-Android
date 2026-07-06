package com.littlehelper.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionKeyResolverTest {

    @Test
    fun resolve_mainAgent() {
        assertEquals("agent:main:main", SessionKeyResolver.resolve("main"))
        assertEquals("agent:main:main", SessionKeyResolver.resolve(""))
        assertEquals("agent:main:main", SessionKeyResolver.resolve("  "))
    }

    @Test
    fun resolve_namedAgent() {
        assertEquals("agent:laoxia:main", SessionKeyResolver.resolve("laoxia"))
        assertEquals("agent:erzi:main", SessionKeyResolver.resolve("erzi"))
    }

    @Test
    fun isValidAgentName_allowsAlphanumericUnderscore() {
        assertTrue(SessionKeyResolver.isValidAgentName("main"))
        assertTrue(SessionKeyResolver.isValidAgentName("laoxia"))
        assertTrue(SessionKeyResolver.isValidAgentName("zhangsan_01"))
        assertFalse(SessionKeyResolver.isValidAgentName("laoxia-1"))
        assertFalse(SessionKeyResolver.isValidAgentName("老夏"))
    }

    @Test
    fun parseAgentNameFromSessionKey_roundTrip() {
        assertEquals("main", SessionKeyResolver.parseAgentNameFromSessionKey("agent:main:main"))
        assertEquals("laoxia", SessionKeyResolver.parseAgentNameFromSessionKey("agent:laoxia:main"))
    }

    @Test
    fun sessionKeyLabel_formatsAgentAndSession() {
        assertEquals("main · main", SessionKeyResolver.sessionKeyLabel("main"))
        assertEquals("laoxia · main", SessionKeyResolver.sessionKeyLabel("laoxia"))
    }
}
