---
name: "weather-display"
description: "天气查询卡片：默认北京、支持指定城市、wttr.in数据源、中文化描述、3日预报"
---

# 天气查询卡片

## 触发条件
- 「查看天气」→ 默认北京
- 「查看上海天气」「上海天气怎么样」→ 指定城市

## MODAL id
固定使用 `skill-weather`

## 数据源
wttr.in 免费 API（`?format=j1`），无需 Key

## 脚本
```
python scripts/weather_card.py --city 北京 --out mobile    # 默认北京
python scripts/weather_card.py --city 上海 --out mobile    # 指定城市
```

## 卡片内容
- 城市名（用户输入值，不用 API 返回的区名）
- 当前温度 + 体感温度
- 天气描述（中文化，英文→中文映射表）
- 今日最高/最低
- 湿度、风速、紫外线
- 3 日预报（含日期和星期）

## 天气描述翻译
内置英文→中文映射表（DESC_CN），覆盖常见天气类型。
大小写不敏感匹配。

## 城市文件名映射
中文城市名 → ASCII 文件名：北京→beijing, 上海→shanghai 等

## 展示协议

响应格式（手机端）：
```
===CHAT===
北京 24°C，局部阵雨。今天 22°~33°。

===MODAL===
{
  "action": "open",
  "blocks": [{
    "id": "skill-weather",
    "type": "webview",
    "title": "北京天气",
    "data": {
      "url": "/__openclaw__/canvas/weather_beijing.html",
      "scrollable": false,
      "fillHeight": true
    }
  }]
}
```

## 视觉设计
- 暗色主题（#0d1117 背景）
- 渐变卡片（#16213e → #1a1a4e）
- 大号温度数字（56px mobile / 64px desktop）
- 3 列网格详情（湿度/风速/紫外线）
- 3 日预报横排

## 脚本位置
`scripts/weather_card.py`
