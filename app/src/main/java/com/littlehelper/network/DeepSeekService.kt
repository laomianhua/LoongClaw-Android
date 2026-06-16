package com.littlehelper.network



import com.google.gson.Gson

import com.google.gson.JsonSyntaxException

import com.littlehelper.BuildConfig

import com.littlehelper.ChatMessage as UiChatMessage
import com.littlehelper.FollowUpContext
import com.littlehelper.VoiceIntentDetector

import com.littlehelper.data.MemoryRecord

import okhttp3.OkHttpClient

import okhttp3.logging.HttpLoggingInterceptor

import retrofit2.Retrofit

import retrofit2.converter.gson.GsonConverterFactory

import java.util.concurrent.TimeUnit
import java.time.LocalDateTime



class DeepSeekService {

    private val gson = Gson()



    private val api: DeepSeekApi by lazy {

        val logging = HttpLoggingInterceptor().apply {

            level = HttpLoggingInterceptor.Level.BASIC

        }

        val client = OkHttpClient.Builder()

            .connectTimeout(30, TimeUnit.SECONDS)

            .readTimeout(60, TimeUnit.SECONDS)

            .writeTimeout(30, TimeUnit.SECONDS)

            .addInterceptor(logging)

            .build()



        Retrofit.Builder()

            .baseUrl("https://api.deepseek.com/")

            .client(client)

            .addConverterFactory(GsonConverterFactory.create(gson))

            .build()

            .create(DeepSeekApi::class.java)

    }



    fun hasApiKey(): Boolean = BuildConfig.DEEPSEEK_API_KEY.isNotBlank()



    suspend fun sendSecretaryTurn(
        history: List<UiChatMessage>,
        memoryContext: String? = null,
        followUpContext: FollowUpContext = FollowUpContext.NONE
    ): LlmResponseParser.ParsedResponse {
        val systemPrompt = prependTimeBaseline(
            if (memoryContext.isNullOrBlank()) {
                SECRETARY_SYSTEM_PROMPT
            } else {
                SECRETARY_SYSTEM_PROMPT + "\n\n# 当前本地已存记录（删除/修改时请参考）\n" + memoryContext
            }
        )

        val apiMessages = buildList {
            add(ChatMessage(role = "system", content = systemPrompt))
            addAll(ChatHistoryBuilder.toApiMessages(history, followUpContext))
        }

        val content = chatCompletions(apiMessages)
        return LlmResponseParser.parse(content)
    }

    /** 秘书口头承诺写库但遗漏 DB_OPS，或确认轮空回时，隐式追问补全结构化块（不进入 UI 聊天记录）。 */
    suspend fun requestDbOpsSelfCorrection(
        history: List<UiChatMessage>,
        memoryContext: String?,
        priorAssistantReply: String,
        followUpContext: FollowUpContext = FollowUpContext.NONE,
        priorIntent: String? = null,
        lastUserText: String? = null
    ): LlmResponseParser.ParsedResponse {
        val correctionUserMessage = buildString {
            if (followUpContext == FollowUpContext.SAVE && !priorIntent.isNullOrBlank()) {
                append("用户已确认要记下：$priorIntent。")
            } else if (!lastUserText.isNullOrBlank() &&
                VoiceIntentDetector.isDeleteRequest(lastUserText)
            ) {
                append("用户要求删除：$lastUserText。")
                append("请从「当前本地已存记录」查匹配项的 id，")
                append("在 operations 中输出一个或多个 op 为 delete 的 JSON（每条记录一个 delete）。")
            } else if (!lastUserText.isNullOrBlank() && lastUserText.contains("提醒")) {
                append("用户要求设置提醒：$lastUserText。")
                append("请输出 insert，必须含 formatted_date_for_alarm（ISO 日期）与 event_time（24 小时制 HH:mm）。")
            }
            append("你刚才忘记输出 ___DB_OPS_START___ 结构化 JSON 块了，请不要说日常废话，直接输出 JSON 块。")
        }
        val correctionHistory = history + listOf(
            UiChatMessage.assistant(priorAssistantReply),
            UiChatMessage.user(correctionUserMessage)
        )
        return sendSecretaryTurn(correctionHistory, memoryContext, followUpContext)
    }



    /** 查询兜底：从最近记录摘要中让 AI 挑选与用户问题最相关的 id 列表。 */
    suspend fun matchRecords(userQuery: String, records: List<com.littlehelper.data.MemoryRecord>): List<Long> {
        if (records.isEmpty()) return emptyList()

        val summariesText = records.joinToString("\n") { "${it.id} : ${it.summary}" }
        val systemPrompt = """
            你是语音小帮手的记录匹配助手。
            只输出 JSON 数组（如 [3, 7, 12] 或 []），不要 markdown，不要解释。
        """.trimIndent()
        val userPrompt = """
            用户问题：$userQuery

            以下是最近记录的摘要（id : summary）：
            $summariesText

            请严格返回与用户问题最相关的记录 id，以 JSON 数组格式返回，如：[3, 7, 12]
            如果没有任何一条相关，返回空数组 []
        """.trimIndent()

        val content = chat(systemPrompt, userPrompt)
        return parseIdArray(content)
    }

    suspend fun planQuery(question: String): QueryPlanPayload {

        val systemPrompt = prependTimeBaseline("""
            你是语音小帮手的查询分析助手。

            请把用户问题转成 JSON，不要输出 markdown。

            字段：

            - category: schedule|birthday|parking|item_place|mood|general|null

            - keywords: 字符串数组

            - prefer_latest: 问停车位置等时 true

            - answer_hint: 回答风格提示，简短中文

            用户若问「今天几号」「后天有什么安排」等，请结合【重要时间基准】理解相对时间后再分析。
        """.trimIndent())



        val content = chat(

            systemPrompt = systemPrompt,

            userPrompt = question

        )

        return parseJson(content, QueryPlanPayload::class.java)

            ?: QueryPlanPayload(keywords = listOf(question), preferLatest = false)

    }



    suspend fun answerQuery(question: String, candidates: List<MemoryRecord>): String {

        val memoriesText = if (candidates.isEmpty()) {

            "（本地暂无相关记录）"

        } else {

            candidates.joinToString("\n\n") { record ->

                buildString {

                    append("ID=${record.id}\n")

                    append("分类=${record.category}\n")

                    append("摘要=${record.summary}\n")

                    append("人物=${record.person.orEmpty()}\n")

                    append("日期=${record.eventDate.orEmpty()}\n")
                    append("时间=${record.eventTime.orEmpty()}\n")
                    append("提醒日期=${record.formattedDateForAlarm.orEmpty()}\n")

                    append("原文=${record.rawText}")

                }

            }

        }



        val systemPrompt = prependTimeBaseline("""
            你是语音小帮手，面向老年用户回答问题。

            要求：

            - 只用中文

            - 回答短、清楚、口语化，不超过3句话

            - 优先依据给定记录，不要编造

            - 没有记录就诚实说还没记下

            - 问停车位置时优先最近一条 parking 记录

            - 用户语音可能把姓名听成同音字；若本地有多条同音不同字的记录，只回答与问题【汉字完全匹配】的那条；若无法区分，如实说有多位读音相近的人

            - 用户问「今天几号」「星期几」「后天有什么安排」等，请结合【重要时间基准】与本地记录中的提醒日期作答

            - 用户问「现在几点」「几点了」「什么时间」时，请直接根据【重要时间基准】中的当前时刻回答，不要说没有记录具体几点

            - 用户列举或汇总多条记录时，必须用「1. 2. 3.」编号分行，禁止挤在一句话里

            - 不要提 JSON、数据库、AI 等词
        """.trimIndent())



        val userPrompt = """

            用户问题：$question



            本地记录：

            $memoriesText

        """.trimIndent()



        return chat(

            systemPrompt = systemPrompt,

            userPrompt = userPrompt

        ).ifBlank { "我还没找到相关记录，您可以再说一遍让我记下来。" }

    }



    private fun prependTimeBaseline(basePrompt: String, now: LocalDateTime = LocalDateTime.now()): String {
        return SystemTimeContext.prependToSystemPrompt(basePrompt, now)
    }

    private suspend fun chat(

        systemPrompt: String,

        userPrompt: String

    ): String {

        return chatCompletions(

            listOf(

                ChatMessage(role = "system", content = systemPrompt),

                ChatMessage(role = "user", content = userPrompt)

            )

        )

    }



    private suspend fun chatCompletions(messages: List<ChatMessage>): String {

        if (!hasApiKey()) {

            throw IllegalStateException("请先在 local.properties 配置 DEEPSEEK_API_KEY")

        }



        val response = api.chatCompletions(

            authorization = "Bearer ${BuildConfig.DEEPSEEK_API_KEY}",

            request = ChatCompletionRequest(

                model = MODEL_FLASH,

                messages = messages

            )

        )



        return response.choices

            ?.firstOrNull()

            ?.message

            ?.content

            ?.trim()

            .orEmpty()

    }



    private fun <T> parseJson(content: String, clazz: Class<T>): T? {

        val json = extractJson(content) ?: return null

        return try {

            gson.fromJson(json, clazz)

        } catch (_: JsonSyntaxException) {

            null

        }

    }



    private fun extractJson(content: String): String? {

        val trimmed = content.trim()

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {

            return trimmed

        }

        val start = trimmed.indexOf('{')

        val end = trimmed.lastIndexOf('}')

        if (start >= 0 && end > start) {

            return trimmed.substring(start, end + 1)

        }

        return null

    }

    private fun extractJsonArray(content: String): String? {
        val trimmed = content.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed
        }
        val start = trimmed.indexOf('[')
        val end = trimmed.lastIndexOf(']')
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        return null
    }

    private fun parseIdArray(content: String): List<Long> {
        val json = extractJsonArray(content) ?: return emptyList()
        return try {
            gson.fromJson(json, Array<Double>::class.java)
                ?.map { it.toLong() }
                .orEmpty()
        } catch (_: JsonSyntaxException) {
            emptyList()
        }
    }



    companion object {

        const val MODEL_FLASH = "deepseek-v4-flash"

        const val MODEL_PRO = "deepseek-v4-pro"



        const val SECRETARY_SYSTEM_PROMPT = """

# Role

你是一个专为中老年人设计的随身语音记事秘书。你温和、有耐心，说话简短清晰。

本地数据保存在 SQLite 数据库（Room）中。你不能直接改库，只能输出【结构化数据库操作指令】，由 App 代为执行 insert / update / delete。

# Workflow

1. 用户输入来自语音转文字（STT），可能有谐音错别字。请结合上下文理解，并在回复里使用【规范汉字】。

2. **评估完整度与写库时机（极重要）**
   - **首轮直接写入（铁律）**：若用户已说清要做的事且时间可推算（如「后天去医院」「后天上午去商场下午去医院」），必须首轮直接输出 DB_OPS insert，简短告知已记下；禁止客气式追问确认（如「需要帮您记下吗？」）。
   - **仅以下三种情况才允许追问**：
     1) 关键字段缺失（如只说「后天」未说做什么）
     2) 同音不同字人名歧义（如王刚 vs 王纲）
     3) person+日期与已有记录完全相同，需确认是否指另一位同音不同字的人
   - **确认轮铁律**：用户对追问肯定答复（「是的」「好的」）后，必须从对话历史提取最初事项，立刻输出带标准日期的 DB_OPS；严禁回复「目前我这里还没有记下任何信息」「没有任何记录」「没有内容被记录」等套话。
   - **【多事件拆分示例】**：若用户说『后天上午去商场下午去医院』，且今天是 2026-06-14。你必须在 operations 数组中同时输出 2 个 op 为 insert 的对象：
     对象 1：summary 为 '后天上午去商场', formatted_date_for_alarm 为 '2026-06-16'；
     对象 2：summary 为 '后天下午去医院', formatted_date_for_alarm 为 '2026-06-16'。
     以此类推，禁止嫌麻烦而逃避输出 JSON！
   - **【相对时刻提醒】**：用户说「10分钟后提醒我」「半小时后叫我」等，必须根据【重要时间基准】的当前时刻心算目标时间，首轮直接 insert；formatted_date_for_alarm 填当天 ISO 日期，event_time 填算出的 24 小时制时刻（如 14:54）；summary 写清提醒内容（如「10分钟后提醒」）。
   - **【定点日程提醒示例】**：用户说「明天上午10点提醒我与八达通联系」，且今天是 2026-06-14。你必须首轮 insert 并输出 DB_OPS：
     summary='明天上午10点与八达通联系'，formatted_date_for_alarm='2026-06-15'，event_time='10:00'，category='schedule'。
     禁止只回复「已记下」而不输出 JSON！

3. **同音不同字（极重要）**
   - 「王纲」与「王刚」拼音相同、汉字不同，可以是两个人，也可以是同一条记录的改字，请根据用户语义判断：
     * 用户说「不是刚才的刚，是大纲的纲」→ 纠正【同一条】记录，必须 `update` + id 把 person 从「王刚」改为「王纲」，禁止说「两个不同的人」，禁止 delete。
     * 用户说「是另外一个/另一位/不是同一个人」→ 只能 insert 新人，禁止 delete 已有记录。
     * 已有王纲1月1，再说王刚1月5且语义是第二个人 → insert 新记录。
   - **禁止只口头说「重新记一下」而不输出 DB_OPS。每次写库必须带 ___DB_OPS_START___ 块。**

4. **删除（极重要）**
   - 仅当用户明确要求删除时才 delete；必须从「当前本地已存记录」查 id，每条待删记录输出一个 `{"op":"delete","id":N}`。
   - 用户说「把某某记录都删掉」且匹配多条时，在 operations 数组中输出多个 delete（禁止只删一条或只口头答应）。
   - **禁止只口头说「删掉了」「这就帮您删」而不输出 DB_OPS。每次删除必须带 ___DB_OPS_START___ 块。**
   - 示例：删 id=3 与 id=5 → `{"status":"success","operations":[{"op":"delete","id":3},{"op":"delete","id":5}]}`

5. **全局清空（极重要）**：仅当用户明确表达「全部删掉」「把里面的东西都清空」「重新开始记」「清空所有记录」等**全局**意图时，可输出 `{"op": "clear_all"}`。App 会弹出确认框，用户确认后才执行；禁止在用户仅想删部分匹配记录时误用 clear_all。

6. **补充/修改同一条（含 update 实体相关性铁律）**
   - 用 update + id + fields，例如补充「下午2点」。
   - **update 前强制实体相关性校验（防跨话题乱写，极重要）**：下发任何 update 操作前，必须在当前用户输入中找到与目标记录【至少一项】实体交集：相同人名、相同地点、相同事件关键词（生日/咖啡/医院/停车等），或用户明确使用「那条」「刚才那个」「上面的」等指代词。
   - **若当前用户输入与所有已存记录毫无实体交集**（例如用户说「今天晚上我吃了剩饭」，库中最新记录是「明天和李总喝咖啡」——两者人名、地点、事件均无交集），严禁强行输出带旧 id 的 update！必须作为独立新事件走 insert（category 视内容选 general/mood/schedule）。

7. **新增记录（极重要）**
   - 用户说「新增/再加一条/添加一条」→ 必须输出 insert，禁止仅回复「已存在」而不写库。
   - 若 person+日期与已有记录【完全相同】（如已有王刚2月6，用户又要王刚2月6），温柔说明已有，并问是否指【另一位同音不同字】的人（如王纲），请用户说明是哪个字。
   - 若用户要记的是同音不同字的第二人（如已有王刚，要加王纲），insert 新记录，person 填不同汉字。
   - 用户刚删了某条又要加回同音不同字的人 → insert，不要因读音相同而拒绝。

# Output Format

【强制 JSON 格式规则】：
你的输出必须严格符合以下两种格式之一：
格式一（成功识别）：
___DB_OPS_START___
{ "status": "success", "operations": [...] }
___DB_OPS_END___

格式二（无法识别/长辈说胡话/算了不记了）：
___DB_OPS_START___
{ "status": "ignore", "reason": "text_too_vague_or_no_intent", "operations": [] }
___DB_OPS_END___
当输出格式二时，你的口语回复只允许留一句极简的引导：'没听懂您想记什么，请您再清楚地说一遍好吗？'，严禁长篇大论或自我解释！

先写给用户看的日常回复，再追加数据库操作块（二选一，优先用 DB_OPS）。

**【口头回复与 App 确认分工（极重要）】**
- 写库成功后的确认播报由 **App 根据 DB_OPS 执行结果统一生成**，你不要自行编造「已记下/记下了/存好了」等成功措辞。
- **未输出 DB_OPS 时**：只能追问缺什么信息，禁止任何表示「已经写入/已经删除」的用语。
- 需要追问时，使用明确问句（如「请问是哪位同音字？」「需要告诉我具体几点吗？」）。

## 推荐：统一数据库操作块

___DB_OPS_START___
{
  "status": "success",
  "operations": [
    {
      "op": "insert",
      "record": {
        "summary": "王纲的生日是1月1日",
        "raw_text": "用户原话",
        "person": "王纲",
        "person_pinyin": "wanggang",
        "category": "birthday",
        "event_date": "1月1日",
        "formatted_date_for_alarm": "2026-01-01",
        "tags": ["王纲", "生日"],
        "is_recurring": true,
        "importance_level": "important",
        "type": "birthday"
      }
    },
    {
      "op": "update",
      "id": 3,
      "fields": {
        "person": "王纲",
        "summary": "王纲的生日是1月1日"
      }
    },
    {
      "op": "delete",
      "id": 2
    },
    {
      "op": "clear_all"
    }
  ]
}
___DB_OPS_END___

规则：
- insert：永远新建，不覆盖旧记录。
- update/delete：必须带 id（从「当前本地已存记录」查）。
- clear_all：单独使用，清空全部本地记录；须用户口头明确表达全局删除/重新开始意图。
- record / fields 请填 person、person_pinyin、event_date、formatted_date_for_alarm、event_time、category、summary、importance_level、type。
- summary 摘要规则：必须纯粹、简练。绝对不要在摘要文字后面硬编码拼接“（紧急）”、“（重要）”等任何括号后缀。
- person 提取规则：如果没有明确提到具体的人名（如张三、李四、王纲等），或者使用的是第一人称口吻（如“我明天要去...”），则代表这个任务的执行者是用户自己。在这种缺省情况下，必须将 person 字段填充为："您"。绝对禁止将“明天下午”、“后天”、“今天晚上”等任何时间状语、时间副词错误地识别或填入 person 字段中。时间有它专门的字段。
- importance_level 严格分级逻辑：
  * critical：仅用于健康医疗（吃药/看病）、涉及具体时间的绝对核心截止事务、或不立刻做会有严重后果的事。
  * important：用于家人生日、涉及金额较大的财务、长期的重要维护，或用户在语气中明确强调了“很重要/千万别忘”的逻辑。
  * normal：日常琐碎、普通聊天、流水账、无强时效性的记录（默认项）。
- type 记录类型逻辑：
  * todo：待办事项（如买菜、拿快递等需要完成的动作）
  * event：日程/活动（如开会、看病等有时间点的事件）
  * note：随手笔记（如密码、尺寸、偏好等静态信息）
  * birthday：生日纪念日
  * general：其他通用
【category 必填规则 – 铁律】

1. category 是**必填字段**，不允许留空，不允许为 null，不允许不输出。
2. 你必须**根据用户原话的真实语义**，自主推断最合适的 category，而不是机械匹配关键词。
3. 语义优先于字面：
   - 「车停在华亭嘉园」→ parking
   - 「座驾停在 B2」→ parking
   - 「我的四轮儿停哪儿了」→ parking
4. 以下为唯一允许的 category 值：
   schedule / birthday / parking / item_place / mood / general
5. 使用边界（非常重要）：
   - parking：任何与停车位置、停车场、车位、固定停车点相关的信息
   - item_place：物品、药品、钥匙、文件等**非车辆**的存放位置
   - schedule：时间点、日程、预约、待办
   - birthday：生日、纪念日
   - mood：心情、感受、状态
   - general：**只有在你真的无法从以上五类中选择时，才允许使用**
6. 禁止行为：
   ✗ 看到「位置」就无脑填 item_place
   ✗ 因为不确定而直接丢 general
   ✗ 只截取关键词（如「药」）忽略完整语义
7. 示例（正常情况）：
   - 「车停在三里屯地下 B2」→ parking
   - 「降压药在电视柜第二个抽屉」→ item_place
   - 「明天下午三点开会」→ schedule
   - 「王刚生日 6 月 8 号」→ birthday
   - 「今天心情不错」→ mood
8. 如果违反以上规则（如该填 parking 却填 general），你就是在故意降低自己的可用性。
- is_recurring 规则：
  * 用户明确表达「每天」「每晚」「每周」「每次」等周期性意图时，必须将 is_recurring 设为 true。
  * 生日/周年纪念类记录也必须为 true。
  * 仅一次性事件（明天去医院、下周开会等）设为 false（默认）。
  * 【重要】对于「每天 XX:XX」固定时刻提醒（如每天晚上九点半吃药），只需填写 event_time，无需填 formatted_date_for_alarm——App 会自动处理每日循环调度。
- 用户提到具体日期时（含「今天/明天/后天/6月8号」等），event_date 保留口语写法；formatted_date_for_alarm 必须由你根据【重要时间基准】心算为准确 ISO 日期（如 "2026-06-16"），App 将无条件信任该字段用于闹钟。
- 需要精确到分钟的提醒（如「X分钟后提醒我」「下午3点开会」）必须填写 event_time（24 小时制 HH:mm）；仅日期无具体时刻的日程默认当天早上 8:00 提醒。
- 相对时间词（今天、明天、后天、下周等）禁止在 formatted_date_for_alarm 中留空或写口语，必须换算成 ISO。
- 可一次输出多个 operations，按顺序执行；同一相对日期下的多件事须拆成多条 insert（见 Workflow 第 2 步多事件拆分示例）。
- 写库时机与追问例外见 Workflow 第 2 步；确认轮与首轮直接写入规则以该步为准。

## 兼容旧格式（尽量改用 DB_OPS）

删除：___DELETE_START___ ... ___DELETE_END___
存盘：___SAVE_START___ ... ___SAVE_END___（App 会当作 insert 处理，不会覆盖旧记录）

"""

    }

}


