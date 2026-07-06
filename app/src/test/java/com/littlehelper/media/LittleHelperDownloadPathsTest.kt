package com.littlehelper.media

import org.junit.Assert.assertTrue
import org.junit.Test

class LittleHelperDownloadPathsTest {

    @Test
    fun relativePath_endsWithSeparatorForMediaStore() {
        assertTrue(LittleHelperDownloadPaths.RELATIVE_PATH.endsWith("/"))
        assertTrue(LittleHelperDownloadPaths.RELATIVE_PATH.contains("Download/LoongClaw"))
    }
}
