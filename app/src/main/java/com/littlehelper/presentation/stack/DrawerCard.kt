package com.littlehelper.presentation.stack

/** 底部抽屉堆栈中的卡片标识；后续扩展新模块时在此追加枚举值即可。 */
enum class DrawerCard(val label: String, val icon: String) {
    NOTEBOOK("记事本", "📝"),
    MAP("地图", "🗺️")
}
