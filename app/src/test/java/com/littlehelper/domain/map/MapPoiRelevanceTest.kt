package com.littlehelper.domain.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPoiRelevanceTest {

    @Test
    fun isPoiRelevant_unionHospital_matches() {
        assertTrue(MapPoiRelevance.isRelevant("协和医院", "北京协和医院(东单院区)"))
        assertTrue(MapPoiRelevance.isRelevant("北京协和医院", "北京协和医院(东单院区)"))
    }

    @Test
    fun isPoiRelevant_fakePlanetHospital_rejectsUnrelatedPoi() {
        assertFalse(MapPoiRelevance.isRelevant("冥王星医院", "天王星商务大厦"))
    }
}
