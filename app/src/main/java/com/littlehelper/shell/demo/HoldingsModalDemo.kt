package com.littlehelper.shell.demo

/**
 * 方案 §10 附录：端到端示例 —— 持仓表格 + 折线图。
 */
object HoldingsModalDemo {

    const val USER_MESSAGE = "帮我看看今天持仓情况"

    val AGENT_RESPONSE = """
===CHAT===
好的，以下是今天的持仓情况。整体总资产约 196.8 万，其中基金部分约 186.5 万。

===MODAL===
{
  "action": "open",
  "blocks": [
    {
      "id": "holdings-table",
      "type": "table",
      "title": "🟢 持仓涨跌一览",
      "data": {
        "headers": [
          {"key": "name", "label": "基金名称"},
          {"key": "amount", "label": "持有金额", "align": "right"},
          {"key": "dailyPnl", "label": "当日收益", "align": "right"},
          {"key": "totalPnl", "label": "累计收益", "align": "right"}
        ],
        "rows": [
          {"name": "纳指ETF", "amount": "35.2万", "dailyPnl": "+1.2%", "totalPnl": "+23.4%"},
          {"name": "沪深300ETF", "amount": "28.5万", "dailyPnl": "-0.3%", "totalPnl": "+5.6%"},
          {"name": "中证500指数", "amount": "22.1万", "dailyPnl": "+0.8%", "totalPnl": "+8.2%"}
        ],
        "options": {
          "highlight": "red_up_green_down"
        }
      }
    },
    {
      "id": "daily-chart",
      "type": "chart/line",
      "title": "总资产近30日走势",
      "data": {
        "series": [
          {
            "name": "总资产",
            "color": "#1890FF",
            "points": [
              {"x": "06-01", "y": 195.0},
              {"x": "06-05", "y": 196.2},
              {"x": "06-10", "y": 194.8},
              {"x": "06-15", "y": 195.5},
              {"x": "06-20", "y": 196.8}
            ]
          }
        ],
        "options": {
          "yLabel": "万元"
        }
      }
    }
  ]
}
===END===
""".trimIndent()

    const val UPDATE_USER_MESSAGE = "加条均线"

    val UPDATE_AGENT_RESPONSE = """
===CHAT===
好的，加了 5 日均线（虚线）。

===MODAL===
{
  "action": "update",
  "blocks": [
    {
      "id": "daily-chart",
      "type": "chart/line",
      "title": "总资产近30日走势",
      "data": {
        "series": [
          {
            "name": "总资产",
            "color": "#1890FF",
            "points": [
              {"x": "06-01", "y": 195.0},
              {"x": "06-05", "y": 196.2},
              {"x": "06-10", "y": 194.8},
              {"x": "06-15", "y": 195.5},
              {"x": "06-20", "y": 196.8}
            ]
          },
          {
            "name": "5日均线",
            "color": "#4ECDC4",
            "dashed": true,
            "points": [
              {"x": "06-01", "y": 195.2},
              {"x": "06-05", "y": 195.8},
              {"x": "06-10", "y": 195.1},
              {"x": "06-15", "y": 195.6},
              {"x": "06-20", "y": 196.5}
            ]
          }
        ],
        "options": {
          "yLabel": "万元"
        }
      }
    }
  ]
}
===END===
""".trimIndent()
}
