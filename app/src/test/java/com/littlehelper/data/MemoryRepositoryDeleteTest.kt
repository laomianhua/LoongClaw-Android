package com.littlehelper.data

import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRepositoryDeleteTest {

    @Test
    fun buildDeleteTerms_matchesCoffeeWithLiZong() {
        val terms = MemoryRepository.buildDeleteTerms(
            target = "和李总喝咖啡",
            tags = listOf("李总", "咖啡")
        )
        assertTrue(terms.any { it.contains("李总") })
        assertTrue(terms.any { it.contains("咖啡") })
    }

    @Test
    fun buildDeleteTerms_stripsDeleteVerbsFromUserUtterance() {
        val terms = MemoryRepository.buildDeleteTerms(
            target = "帮我删除和李总喝咖啡的记录",
            tags = emptyList()
        )
        assertTrue(terms.any { it.contains("李总") || it.contains("咖啡") })
    }
}
