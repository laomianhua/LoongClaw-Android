package com.littlehelper.settings

/**
 * App 界面语言（设置 / 上传 / 我的文件等 scoped strings）。
 * 未覆盖的 string 仍回退到 values/ 中文。
 */
enum class AppUiLanguage(val wire: String) {
    ZH("zh"),
    EN("en");

    companion object {
        fun fromWire(value: String?): AppUiLanguage = when (value) {
            EN.wire -> EN
            else -> ZH
        }
    }
}
