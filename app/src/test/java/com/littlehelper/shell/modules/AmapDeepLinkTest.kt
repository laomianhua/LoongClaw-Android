package com.littlehelper.shell.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmapDeepLinkTest {

    @Test
    fun isDeepLink_recognizesAmapSchemes() {
        assertTrue(
            AmapDeepLink.isDeepLink(
                "androidamap://viewMap?sourceApplication=littlehelper&poiname=test&lat=39.9&lon=116.4&dev=0"
            )
        )
        assertTrue(
            AmapDeepLink.isDeepLink(
                "amapuri://route/plan/?slat=39.9&slon=116.4&sname=A&dlat=40.0&dlon=116.5&dname=B&dev=0&t=0"
            )
        )
        assertFalse(AmapDeepLink.isDeepLink("https://uri.amap.com/marker?position=116.4,39.9"))
        assertFalse(AmapDeepLink.isDeepLink("https://webrd01.is.autonavi.com/appmaptile?x=1&y=2&z=3"))
    }

    @Test
    fun toWebFallbackUrl_viewMap() {
        val url =
            "androidamap://viewMap?sourceApplication=littlehelper&poiname=%E5%A4%A9%E5%AE%89%E9%97%A8&lat=39.9087&lon=116.3975&dev=0"
        val fallback = AmapDeepLink.toWebFallbackUrl(url)
        assertNotNull(fallback)
        assertTrue(fallback!!.startsWith("https://uri.amap.com/marker?"))
        assertTrue(fallback.contains("position=116.3975,39.9087"))
        assertTrue(fallback.contains("name="))
    }

    @Test
    fun toWebFallbackUrl_navi() {
        val url =
            "androidamap://navi?sourceApplication=littlehelper&poiname=%E5%A4%A9%E5%AE%89%E9%97%A8&lat=39.9087&lon=116.3975&dev=0"
        val fallback = AmapDeepLink.toWebFallbackUrl(url)
        assertNotNull(fallback)
        assertTrue(fallback!!.startsWith("https://uri.amap.com/navigation?"))
        assertTrue(fallback.contains("to="))
        assertTrue(fallback.contains("mode=car"))
    }

    @Test
    fun toWebFallbackUrl_routePlan() {
        val url =
            "amapuri://route/plan/?sid=&slat=39.9785&slon=116.3617&sname=%E5%8D%8E%E4%BA%AD%E5%98%89%E5%9B%AD&did=&dlat=39.9087&dlon=116.3975&dname=%E5%A4%A9%E5%AE%89%E9%97%A8&dev=0&t=0"
        val fallback = AmapDeepLink.toWebFallbackUrl(url)
        assertNotNull(fallback)
        assertTrue(fallback!!.startsWith("https://uri.amap.com/navigation?"))
        assertTrue(fallback.contains("from="))
        assertTrue(fallback.contains("to="))
        assertTrue(fallback.contains("mode=car"))
    }

    @Test
    fun toWebFallbackUrl_unknownScheme_returnsNull() {
        assertNull(AmapDeepLink.toWebFallbackUrl("androidamap://keywordNavi?keyword=test"))
    }

    @Test
    fun travelModeFromT_mapsModes() {
        assertEquals("car", AmapDeepLink.travelModeFromT("0"))
        assertEquals("bus", AmapDeepLink.travelModeFromT("1"))
        assertEquals("walk", AmapDeepLink.travelModeFromT("2"))
        assertEquals("ride", AmapDeepLink.travelModeFromT("3"))
        assertEquals("car", AmapDeepLink.travelModeFromT(null))
    }
}
