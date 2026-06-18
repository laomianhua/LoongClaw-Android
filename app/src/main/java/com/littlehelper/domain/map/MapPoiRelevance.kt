package com.littlehelper.domain.map

/** VIEW_LOCATION 用：判断高德 POI 标题是否与用户查询词相关。 */
object MapPoiRelevance {

    fun isRelevant(keyword: String, poiTitle: String): Boolean {
        val key = keyword.filterNot { it.isWhitespace() }
        val title = poiTitle.filterNot { it.isWhitespace() }
        if (key.length < 2 || title.isEmpty()) return false
        if (title.contains(key)) return true

        val keyCore = distinctivePlaceCore(key)
        if (keyCore.length >= 2 && title.contains(keyCore)) return true

        val first = keyCore.firstOrNull() ?: return false
        if (!title.contains(first)) return false

        val generic = setOf('医', '院', '大', '厦', '中', '心', '广', '场', '市', '区', '县', '路', '街', '星')
        for (len in minOf(keyCore.length, 4) downTo 2) {
            for (i in 0..keyCore.length - len) {
                val sub = keyCore.substring(i, i + len)
                if (title.contains(sub) && sub.any { it !in generic }) {
                    return true
                }
            }
        }
        return false
    }

    private fun distinctivePlaceCore(name: String): String {
        return name
            .removeSuffix("在哪里").removeSuffix("在哪").removeSuffix("怎么走")
            .removeSuffix("医院").removeSuffix("诊所").removeSuffix("门诊部")
            .removeSuffix("商务大厦").removeSuffix("大厦").removeSuffix("中心").removeSuffix("广场")
    }
}
