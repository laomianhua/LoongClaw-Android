package com.littlehelper.shell.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StoredImageAssetResolverTest {

    @Test
    fun parseViewStoredImgUrl_fromRelativeCanvasUrl() {
        val asset = StoredImageAssetResolver.parseViewStoredImgUrl(
            rawUrl = "/__openclaw__/canvas/view_stored_img.html?f=94b13de811b149238936d980384cc0ef_photo.jpg&name=会员卡",
            title = "会员卡"
        )
        requireNotNull(asset)
        assertEquals("94b13de811b149238936d980384cc0ef", asset.fileId)
        assertEquals("会员卡", asset.displayName)
        assertEquals("94b13de811b149238936d980384cc0ef_photo.jpg", asset.storageFileName)
        assertEquals(
            "/__openclaw__/file/download/94b13de811b149238936d980384cc0ef_photo.jpg",
            asset.downloadUrl
        )
    }

    @Test
    fun parseDirectImageUrl_fromCanvasPath() {
        val asset = StoredImageAssetResolver.parseDirectImageUrl(
            rawUrl = "/__openclaw__/canvas/storage/abc123_cat.jpg",
            title = "大胖"
        )
        requireNotNull(asset)
        assertEquals("大胖", asset.displayName)
        assertEquals("/__openclaw__/file/download/abc123_cat.jpg", asset.downloadUrl)
    }
}
