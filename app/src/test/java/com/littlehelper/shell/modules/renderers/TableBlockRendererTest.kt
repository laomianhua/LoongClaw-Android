package com.littlehelper.shell.modules.renderers

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TableBlockRendererTest {

    @Test
    fun parseTableData_schemaObjectFormat() {
        val data = JsonParser.parseString(
            """
            {
              "headers": [
                {"key": "name", "label": "基金名称"},
                {"key": "amount", "label": "金额", "align": "right"}
              ],
              "rows": [
                {"name": "纳指ETF", "amount": "35.2万"}
              ]
            }
            """.trimIndent()
        ).asJsonObject

        val parsed = parseTableData(data)

        assertEquals(2, parsed.headers.size)
        assertEquals("基金名称", parsed.headers[0].label)
        assertEquals("纳指ETF", parsed.rows.single()["name"])
    }

    @Test
    fun parseTableData_stringArrayFormat_gatewayMistake() {
        val data = JsonParser.parseString(
            """
            {
              "headers": ["基金名称", "持有金额", "收益率"],
              "rows": [
                ["沪深300ETF", "580,000", "+2.12%"],
                ["中证500ETF", "350,000", "-1.66%"]
              ]
            }
            """.trimIndent()
        ).asJsonObject

        val parsed = parseTableData(data)

        assertEquals(3, parsed.headers.size)
        assertEquals("col0", parsed.headers[0].key)
        assertEquals("基金名称", parsed.headers[0].label)
        assertEquals(2, parsed.rows.size)
        assertEquals("沪深300ETF", parsed.rows[0]["col0"])
        assertEquals("+2.12%", parsed.rows[0]["col2"])
    }

    @Test
    fun parseTableData_empty_returnsEmpty() {
        val parsed = parseTableData(JsonParser.parseString("{}").asJsonObject)
        assertTrue(parsed.headers.isEmpty())
        assertTrue(parsed.rows.isEmpty())
    }
}
