package com.littlehelper.shell.modules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryDeepLinkTest {

    @Test
    fun isDeepLink_recognizesGalleryDownload() {
        assertTrue(GalleryDeepLink.isDeepLink("littlehelper://gallery/download?index=0"))
        assertTrue(GalleryDeepLink.isDeepLink("littlehelper://gallery/download?index=3"))
        assertFalse(GalleryDeepLink.isDeepLink("littlehelper://gallery/toggle?index=0"))
        assertFalse(GalleryDeepLink.isDeepLink("https://example.com/gallery"))
    }
}
