package com.littlehelper.data.todo

/** 判断用户是否在反馈「某条待办已完成」。 */
object TodoCompletionHelper {

  private val completionPatterns = listOf(
      Regex("""(已经|早就|刚|刚)?(吃|喝|用|服)(完|过|了|好啦?)"""),
      Regex("""(已经|刚)?(拿|取|办|做|弄|处理)(完|好|回来|掉了?)"""),
      Regex("""(已经)?完成(了|啦)?"""),
      Regex("""(已经)?搞定(了|啦)?"""),
      Regex("""(已经)?做好了"""),
      Regex("""好的?[，,]?已经"""),
      Regex("""(没问题|搞定了)"""),
  )

  fun looksLikeCompletionUtterance(text: String): Boolean {
      val normalized = text.trim()
      if (normalized.isEmpty()) return false
      return completionPatterns.any { it.containsMatchIn(normalized) }
  }
}
