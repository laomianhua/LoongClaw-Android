package com.littlehelper.network

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 每次请求 DeepSeek 前，将手机系统时间注入 system prompt 顶部。 */
object SystemTimeContext {
    private val chineseWeekdays = mapOf(
        DayOfWeek.MONDAY to "一",
        DayOfWeek.TUESDAY to "二",
        DayOfWeek.WEDNESDAY to "三",
        DayOfWeek.THURSDAY to "四",
        DayOfWeek.FRIDAY to "五",
        DayOfWeek.SATURDAY to "六",
        DayOfWeek.SUNDAY to "日"
    )

    fun buildTimeBaselinePrefix(now: LocalDateTime = LocalDateTime.now()): String {
        val date = now.toLocalDate()
        val dayOfWeek = chineseWeekdays[date.dayOfWeek].orEmpty()
        val clock = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        val exampleIso = date.plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE)
        return "【重要时间基准】：当前系统时间是 ${date.year}年${date.monthValue}月${date.dayOfMonth}日 $clock，星期$dayOfWeek。" +
            "请务必以此日期与时刻为绝对基准，去理解用户口中的「今天」、「明天」、「后天」、「下周」等相对日期词，" +
            "以及「10分钟后」「半小时后」「下午3点」等相对或绝对时刻。" +
            "用户问「现在几点」「几点了」时，请直接据此准确回答当前时刻。" +
            "在输出 DB_OPS 的 formatted_date_for_alarm 字段时，必须由你（AI）计算出准确的 ISO 日期字符串（如 $exampleIso）；" +
            "若需精确到分钟的提醒，还须填写 event_time（24 小时制 HH:mm，如 $clock）。"
    }

    fun prependToSystemPrompt(basePrompt: String, now: LocalDateTime = LocalDateTime.now()): String {
        return buildTimeBaselinePrefix(now) + "\n\n" + basePrompt.trim()
    }
}
