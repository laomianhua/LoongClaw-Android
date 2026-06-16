package com.littlehelper.viewmodel



import android.app.Application
import android.util.Log

import androidx.lifecycle.AndroidViewModel

import androidx.lifecycle.viewModelScope

import com.littlehelper.AppPhase
import com.littlehelper.FollowUpContext

import com.littlehelper.ChatMessage

import com.littlehelper.ChatRole

import com.littlehelper.MemoryCategory

import com.littlehelper.VoiceAction

import com.littlehelper.VoiceIntentDetector

import com.littlehelper.data.DeleteRequestHelper
import com.littlehelper.data.DisambiguationHelper
import com.littlehelper.data.RecordListQueryHelper
import com.littlehelper.data.AppDatabase
import com.littlehelper.data.MemoryChangeConfirmationBuilder
import com.littlehelper.data.MemoryMatch
import com.littlehelper.data.MemoryOperation
import com.littlehelper.data.MemoryOperationExecutor
import com.littlehelper.data.MemoryRecordPayload

import com.littlehelper.data.MemoryRecord

import com.littlehelper.data.MemoryRepository

import com.littlehelper.data.NameMatcher
import com.littlehelper.data.PinyinHelper

import com.littlehelper.network.ChatHistoryBuilder

import com.littlehelper.network.DeepSeekService
import com.littlehelper.network.QueryPlanPayload

import com.littlehelper.network.DeletePayload
import com.littlehelper.network.LlmResponseValidator
import com.littlehelper.network.SaveConfirmationHelper
import com.littlehelper.network.SavePayload
import com.littlehelper.reminder.ReminderScheduler

import com.littlehelper.network.AsrService
import com.littlehelper.network.VolcengineAsrService
import com.littlehelper.speech.AudioRecorderManager
import java.io.File

import com.littlehelper.tts.TtsManager

import kotlinx.coroutines.flow.MutableSharedFlow

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.SharedFlow

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asSharedFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.update

import kotlinx.coroutines.launch



data class MainUiState(

    val phase: AppPhase = AppPhase.IDLE,

    val messages: List<ChatMessage> = emptyList(),

    val isListening: Boolean = false,

    /** AI 追问查询补充，下次按住用短答 STT 模式。 */
    val followUpListening: Boolean = false,

    /** AI 追问是否记下，下次按住确认。 */
    val saveConfirmListening: Boolean = false,

    /** 正在等用户选第几个同音记录。 */
    val choosingRecord: Boolean = false,

    /** AI 明确拒绝（status=ignore）后，等待用户按住重说。 */
    val retryListening: Boolean = false,

    /** TTS 正在播报的助手消息 id，用于高亮气泡。 */
    val speakingMessageId: String? = null

)



class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val IGNORE_GUIDANCE = "没听懂您想记什么，请您再清楚地说一遍好吗？"
    }

    private val repository = MemoryRepository(AppDatabase.getInstance(application).memoryDao())

    private val memoryOperationExecutor = MemoryOperationExecutor(repository)

    private val deepSeekService = DeepSeekService()

    private val reminderScheduler = ReminderScheduler(application)



    val recordsFlow: kotlinx.coroutines.flow.Flow<List<com.littlehelper.data.MemoryRecord>> = repository.getAllFlow()

    fun toggleTodoDone(record: com.littlehelper.data.MemoryRecord) {
        viewModelScope.launch {
            val updated = record.copy(done = !record.done)
            repository.update(updated)
        }
    }

    fun deleteRecord(record: com.littlehelper.data.MemoryRecord) {
        viewModelScope.launch {
            repository.delete(record)
        }
    }

    fun clearChatMessages() {
        _uiState.update { state ->
            // 保留第一条欢迎语，清空其他所有对话
            val welcomeMessage = state.messages.firstOrNull()
            val newMessages = if (welcomeMessage != null && welcomeMessage.text.contains("我是语音小帮手")) {
                listOf(welcomeMessage)
            } else {
                emptyList()
            }
            state.copy(messages = newMessages, phase = AppPhase.IDLE, isListening = false)
        }
    }

    private val _uiState = MutableStateFlow(

        MainUiState(

            messages = listOf(

                ChatMessage.assistant(

                    application.getString(com.littlehelper.R.string.welcome_message)

                )

            )

        )

    )

    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _clearAllConfirmation = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val clearAllConfirmation: SharedFlow<Unit> = _clearAllConfirmation.asSharedFlow()



    private var audioRecorderManager: AudioRecorderManager? = null

    private val asrService: AsrService = VolcengineAsrService()

    /** 录音完成后、发送前临时持有文件引用，确保 finally 块能删除它。 */
    private var pendingAudioFile: File? = null

    private var ttsManager: TtsManager? = null

    private var pendingAction: VoiceAction? = null

    private var lastSavedRecordId: Long? = null

    private var userTurnsSinceLastSave: Int = 0

    private var followUpContext = FollowUpContext.NONE

    /** 查询会话的原问题；QUERY 追问时与短答合并后再次检索。 */
    private var pendingQueryQuestion: String? = null

    /** 已确认的人名（如 夏子→夏子杭），用于 STT 误听为涵/航 时的查询。 */
    private val confirmedPersonByPrefix = mutableMapOf<String, String>()

    /** 拼音匹配到多条记录时，等待用户说「第几个」。 */
    private var pendingDisambiguationRecords: List<MemoryRecord>? = null
    private var pendingDisambiguationQuestion: String? = null



    fun attachAudioRecorderManager(manager: AudioRecorderManager) {

        audioRecorderManager = manager

        manager.onError = { message ->

            removePartialUserMessage()

            speakAsrRetry(mapAsrErrorMessage(message))

        }

    }



    fun attachTtsManager(manager: TtsManager) {

        ttsManager = manager

    }



    fun onHoldStart() {

        val phase = _uiState.value.phase

        if (phase == AppPhase.PROCESSING || phase == AppPhase.SENDING) return

        pendingAction = null

        ttsManager?.stop()

        // 重录：丢弃上一次录好但尚未发送的文件
        pendingAudioFile?.delete()
        pendingAudioFile = null
        audioRecorderManager?.cancel()

        _uiState.update {

            it.copy(

                phase = AppPhase.RECORDING,

                isListening = true,

                speakingMessageId = null,

                retryListening = false

            )

        }

        removePartialUserMessage()

        addPartialUserMessage()

        updatePartialUserMessage("🎙 录音中…")

        audioRecorderManager?.start()

    }

    private fun syncFollowUpUiState() {
        _uiState.update {
            it.copy(
                followUpListening = followUpContext == FollowUpContext.QUERY,
                saveConfirmListening = followUpContext == FollowUpContext.SAVE,
                choosingRecord = pendingDisambiguationRecords != null
            )
        }
    }

    private fun mapAsrErrorMessage(raw: String?): String {
        val msg = raw ?: "转文字失败，请重录一遍"
        return when {
            pendingDisambiguationRecords != null -> "没录到声音，请按住按钮，直接说：第一个，或者 2"
            followUpContext == FollowUpContext.QUERY -> "没录到，请按住按钮，直接说您要查的内容"
            followUpContext == FollowUpContext.SAVE -> "没录到，请按住按钮，说日期或时间，例如：1月1号"
            else -> msg
        }
    }



    fun onHoldCancel() {
        if (_uiState.value.phase == AppPhase.RECORDING) {
            audioRecorderManager?.cancel()
            pendingAudioFile = null
            removePartialUserMessage()
            _uiState.update { it.copy(phase = AppPhase.IDLE, isListening = false) }
        }
    }

    fun onHoldEnd(durationMs: Long) {

        if (_uiState.value.phase != AppPhase.RECORDING) return

        if (durationMs < 500) {
            onHoldCancel()
            return
        }

        // 立即同步更新状态，防止在协程启动前发生连击导致重复发送
        _uiState.update { it.copy(phase = AppPhase.SENDING, isListening = false) }

        val file = audioRecorderManager?.stop() ?: run {
            _uiState.update { it.copy(phase = AppPhase.IDLE) }
            return
        }

        pendingAudioFile = file

        // 直接触发发送，跳过 RECORDING_FINISHED 阶段
        onRecordingFinished(file)

    }



    private fun onRecordingFinished(audioFile: File) {
        viewModelScope.launch {

            try {

                if (!asrService.hasConfig()) {

                    speakAsrRetry("请先配置火山引擎 Key，再试一遍")

                    return@launch

                }

                val result = asrService.transcribe(audioFile)

                result.fold(

                    onSuccess = { text ->

                        finalizeUserMessage(text)

                        if (text.isNotBlank()) {

                            onSpeechFinished(detectAction(text), text)

                        } else {

                            speakAsrRetry("没有识别到内容，请重新录一遍")

                        }

                    },

                    onFailure = { error ->

                        removePartialUserMessage()

                        speakAsrRetry(mapAsrErrorMessage(error.message))

                    }

                )

            } finally {

                cleanupAudioFile(audioFile)

            }

        }

    }



    private fun cleanupAudioFile(file: File) {

        file.delete()

        if (pendingAudioFile == file) pendingAudioFile = null

    }



    private fun speakAsrRetry(message: String) {

        addAssistantMessage(message, isError = true)

        _uiState.update {

            it.copy(phase = AppPhase.IDLE, isListening = false, retryListening = true)

        }

        speakAssistantText(

            text = message,

            phaseWhileSpeaking = AppPhase.IDLE,

            onCompletePhase = AppPhase.IDLE

        )

    }



    fun onReminderOpened(message: String?) {

        if (message.isNullOrBlank()) return

        addAssistantMessage("今日提醒：$message")

        speakAssistantText(message)

    }



    private fun onSpeechFinished(action: VoiceAction, text: String) {

        pendingAction = null

        val lastAssistantMessage = _uiState.value.messages
            .filterNot { it.isPartial || it.isError }
            .lastOrNull { it.role == ChatRole.ASSISTANT }
            ?.text

        if (DeleteRequestHelper.isDeleteCancellation(text) &&
            VoiceIntentDetector.isAwaitingDeleteChoice(followUpContext, lastAssistantMessage)
        ) {
            handleDeleteCancellation()
            return
        }

        if (pendingDisambiguationRecords != null) {
            if (followUpContext == FollowUpContext.DELETE) {
                handleDeleteDisambiguationChoice(text)
            } else {
                handleDisambiguationChoice(text)
            }
            return
        }

        val fromQueryFollowUp = followUpContext == FollowUpContext.QUERY
        val wasSaveFollowUp = followUpContext == FollowUpContext.SAVE
        followUpContext = FollowUpContext.NONE
        syncFollowUpUiState()

        if (action == VoiceAction.SAVE && DeleteRequestHelper.isVagueDeleteRequest(text)) {
            handleVagueDeleteRequest()
            return
        }

        _uiState.update {

            it.copy(phase = AppPhase.PROCESSING, isListening = false)

        }

        if (RecordListQueryHelper.isListAllRecordsQuestion(text)) {
            queryMemory(text)
            return
        }

        when (action) {
            VoiceAction.QUERY -> queryMemory(text, fromQueryFollowUp = fromQueryFollowUp)
            VoiceAction.SAVE -> {
                pendingQueryQuestion = null
                processWithSecretary(wasSaveFollowUp = wasSaveFollowUp)
            }
        }

    }



    private fun processWithSecretary(wasSaveFollowUp: Boolean = false) {

        viewModelScope.launch {

            var spoke = false
            try {

                if (!deepSeekService.hasApiKey()) {

                    setError("请先在 local.properties 配置 DEEPSEEK_API_KEY")
                    spoke = true
                    return@launch

                }

                addAssistantMessage("正在思考…")

                val history = _uiState.value.messages
                    .filterNot { it.role == ChatRole.ASSISTANT && ChatHistoryBuilder.isStatusMessage(it.text) }
                    .withoutFailedTurns()

                val lastUserText = history.lastOrNull { it.role == ChatRole.USER }?.text.orEmpty()

                val memoryContext = buildMemoryContextForLlm()

                val followUpContext = if (wasSaveFollowUp) FollowUpContext.SAVE else FollowUpContext.NONE

                val initialResponse = deepSeekService.sendSecretaryTurn(
                    history,
                    memoryContext,
                    followUpContext
                )
                val resolved = resolveSecretaryResponse(
                    history,
                    memoryContext,
                    initialResponse,
                    followUpContext,
                    lastUserText
                )

                if (isAiIgnoredResponse(resolved.executionResponse)) {
                    handleAiIgnoredResponse()
                    spoke = true
                    return@launch
                }

                val execResult = executeMemoryChanges(resolved.executionResponse, lastUserText)
                val reply = buildAppConfirmationReply(execResult)
                    ?: resolved.displayReply.ifBlank { "好的，我在听。" }

                replaceLastStatusMessage(reply)

                noteSaveFollowUpInvitation(reply)
                noteDeleteFollowUpInvitation(reply)

                _uiState.update { it.copy(retryListening = false) }

                speakAssistantText(reply)
                spoke = true

            } catch (e: Exception) {

                setError(e.message ?: "处理失败，请稍后再试")
                spoke = true

            } finally {

                if (!spoke) {
                    _uiState.update { it.copy(phase = AppPhase.IDLE, isListening = false) }
                }

            }

        }

    }



    private fun resolvePersonName(name: String): String {
        if (name.length < 2) return name
        val prefix = name.take(2)
        return confirmedPersonByPrefix[prefix]?.takeIf { name.startsWith(prefix) } ?: name
    }

    private suspend fun buildMemoryContextForLlm(): String {
        val records = repository.getAll().take(12)
        if (records.isEmpty()) return "（暂无记录）"
        return records.joinToString("\n") { record ->
            buildString {
                append("- id=${record.id}")
                append(" person=${record.person.orEmpty()}")
                append(" pinyin=${record.personPinyin.orEmpty()}")
                append(" category=${record.category}")
                append(" date=${record.eventDate.orEmpty()}")
                append(" summary=${record.summary}")
            }
        }
    }

    private suspend fun resolveSecretaryResponse(
        history: List<ChatMessage>,
        memoryContext: String,
        initial: com.littlehelper.network.LlmResponseParser.ParsedResponse,
        followUpContext: FollowUpContext = FollowUpContext.NONE,
        lastUserText: String = ""
    ): ResolvedSecretaryResponse {
        val defaultReply = initial.reply.ifBlank { "好的，我在听。" }
        val interceptEmptySaveReply = LlmResponseValidator.needsSaveConfirmEmptyReplyCorrection(
            initial,
            followUpContext,
            lastUserText
        )
        val interceptFailedDelete = LlmResponseValidator.needsDeleteWithoutOpsCorrection(
            initial,
            lastUserText
        )
        val saveTurnNeedsOps = LlmResponseValidator.needsSaveTurnWithoutOpsCorrection(
            initial,
            lastUserText
        )
        if (!LlmResponseValidator.needsDbOpsSelfCorrection(initial, followUpContext, lastUserText)) {
            return ResolvedSecretaryResponse(defaultReply, initial)
        }

        val priorIntent = if (followUpContext == FollowUpContext.SAVE) {
            SaveConfirmationHelper.findPriorUserIntent(history)
        } else {
            null
        }
        val corrected = deepSeekService.requestDbOpsSelfCorrection(
            history,
            memoryContext,
            initial.reply,
            followUpContext,
            priorIntent,
            lastUserText
        )
        val hasCorrectedOps = LlmResponseValidator.hasActionableDbOps(corrected)
        if (isAiIgnoredResponse(corrected)) {
            return ResolvedSecretaryResponse(IGNORE_GUIDANCE, corrected)
        }
        val executionResponse = if (hasCorrectedOps) {
            initial.copy(
                dbOpsPayload = corrected.dbOpsPayload,
                savePayload = corrected.savePayload,
                deletePayload = corrected.deletePayload
            )
        } else {
            initial
        }
        val displayReply = when {
            interceptEmptySaveReply && !hasCorrectedOps ->
                "正在帮您记下…"
            interceptFailedDelete && !hasCorrectedOps ->
                "抱歉，还没删成功，请再说一遍要删哪几条。"
            saveTurnNeedsOps && !hasCorrectedOps ->
                "抱歉，还没记成功，请再说一遍要记的内容。"
            else ->
                defaultReply
        }
        return ResolvedSecretaryResponse(displayReply, executionResponse)
    }

    private fun isAiIgnoredResponse(
        response: com.littlehelper.network.LlmResponseParser.ParsedResponse
    ): Boolean {
        return response.dbOpsPayload?.status == "ignore"
    }

    private fun handleAiIgnoredResponse() {
        followUpContext = FollowUpContext.NONE
        pendingDisambiguationRecords = null
        pendingDisambiguationQuestion = null
        pendingQueryQuestion = null
        syncFollowUpUiState()

        replaceLastStatusMessage(IGNORE_GUIDANCE)

        _uiState.update {
            it.copy(
                retryListening = true,
                followUpListening = false,
                saveConfirmListening = false,
                choosingRecord = false
            )
        }

        speakAssistantText(IGNORE_GUIDANCE)
    }

    private suspend fun buildAppConfirmationReply(
        execResult: MemoryOperationExecutor.ExecutionResult?
    ): String? {
        execResult ?: return null
        val affectedIds = (
            execResult.insertedIds +
                execResult.updatedIds +
                execResult.deletedIds
            ).distinct()
        val recordsById = affectedIds.mapNotNull { id ->
            repository.getById(id)?.let { id to it }
        }.toMap()
        return MemoryChangeConfirmationBuilder.build(execResult, recordsById)
    }

    private data class ResolvedSecretaryResponse(
        val displayReply: String,
        val executionResponse: com.littlehelper.network.LlmResponseParser.ParsedResponse
    )

    private suspend fun executeMemoryChanges(
        response: com.littlehelper.network.LlmResponseParser.ParsedResponse,
        lastUserText: String
    ): MemoryOperationExecutor.ExecutionResult? {
        if (isAiIgnoredResponse(response)) return null

        val operations = mutableListOf<MemoryOperation>()

        response.dbOpsPayload?.operations?.let { operations.addAll(it) }

        if (operations.isEmpty()) {
            response.deletePayload?.let { payload ->
                toDeleteOperation(payload, lastUserText)?.let { operations.add(it) }
            }
            if (!VoiceIntentDetector.isDeleteRequest(lastUserText)) {
                response.savePayload?.let { operations.add(it.toInsertOperation(lastUserText)) }
            }
        }

        if (operations.isEmpty()) return null

        val result = memoryOperationExecutor.execute(operations)
        if (result.pendingClearAll) {
            _clearAllConfirmation.tryEmit(Unit)
            return result
        }
        result.insertedIds.lastOrNull()?.let { lastSavedRecordId = it }
        result.deletedIds.forEach { id ->
            reminderScheduler.cancelReminder(id)
            if (lastSavedRecordId == id) lastSavedRecordId = null
        }
        (result.insertedIds + result.updatedIds).distinct().forEach { id ->
            repository.getById(id)?.let { record ->
                reminderScheduler.scheduleIfNeeded(record)
            }
        }
        userTurnsSinceLastSave = 0
        return result
    }

    fun confirmClearAllRecords() {
        viewModelScope.launch {
            repository.getAll().forEach { record ->
                reminderScheduler.cancelReminder(record.id)
            }
            repository.clearAllRecords()
            lastSavedRecordId = null
            pendingDisambiguationRecords = null
            pendingDisambiguationQuestion = null
            pendingQueryQuestion = null
            followUpContext = FollowUpContext.NONE
            confirmedPersonByPrefix.clear()
            syncFollowUpUiState()

            val doneMessage = getApplication<Application>().getString(
                com.littlehelper.R.string.clear_all_done_message
            )
            _uiState.value = MainUiState(
                phase = AppPhase.IDLE,
                messages = listOf(ChatMessage.assistant(doneMessage))
            )
            speakAssistantText(doneMessage)
        }
    }

    private suspend fun toDeleteOperation(
        payload: DeletePayload,
        userUtterance: String
    ): MemoryOperation? {
        val target = payload.target?.trim().orEmpty().ifBlank { userUtterance.trim() }
        if (target.isBlank()) return null
        val tags = payload.tags.orEmpty().filter { it.isNotBlank() }
            .ifEmpty { inferDeleteTags(userUtterance) }
        val matches = repository.findRecordsForDelete(target, tags)
        return when (matches.size) {
            1 -> MemoryOperation(op = "delete", id = matches.first().id)
            0 -> null
            else -> MemoryOperation(
                op = "delete",
                match = MemoryMatch(
                    person = NameMatcher.extractPersonNames(target).firstOrNull(),
                    category = if (target.contains("生日")) MemoryCategory.BIRTHDAY.value else null
                )
            )
        }
    }

    private fun SavePayload.toInsertOperation(userText: String): MemoryOperation {
        val summaryText = summary.orEmpty().ifBlank { userText }
        return MemoryOperation(
            op = "insert",
            record = MemoryRecordPayload(
                summary = summaryText,
                rawText = rawText ?: userText,
                person = person,
                tags = tags,
                category = inferCategoryFromPayload(summary, tags.orEmpty()),
                eventDate = Regex("""(\d{1,2}月\d{1,2}[日号]?)""").find(summaryText)?.groupValues?.get(1),
                isRecurring = inferCategoryFromPayload(summary, tags.orEmpty()) == MemoryCategory.BIRTHDAY.value
            )
        )
    }

    private fun SavePayload.inferCategoryFromPayload(summary: String?, tags: List<String>): String {
        val text = (summary.orEmpty()) + tags.joinToString("")
        return when {
            text.contains("生日") -> MemoryCategory.BIRTHDAY.value
            text.contains("停车") || text.contains("车位") -> MemoryCategory.PARKING.value
            hasTodo -> MemoryCategory.SCHEDULE.value
            else -> MemoryCategory.GENERAL.value
        }
    }

    private fun inferDeleteTags(text: String): List<String> {
        return buildList {
            addAll(NameMatcher.extractPersonNames(text))
            if (text.contains("咖啡")) add("咖啡")
            if (text.contains("停车") || text.contains("车位")) add("停车")
            if (text.contains("生日")) add("生日")
            if (text.contains("万象城")) add("万象城")
        }.distinct()
    }

    private fun findPersonMatches(question: String, candidates: List<MemoryRecord>): List<MemoryRecord> {
        val queryNames = NameMatcher.extractPersonNames(question).map { resolvePersonName(it) }
        return NameMatcher.resolvePersonMatches(candidates, queryNames)
    }

    private fun findBirthdayMatches(question: String, candidates: List<MemoryRecord>): List<MemoryRecord> {
        if (!question.contains("生日")) return emptyList()
        val queryNames = NameMatcher.extractPersonNames(question).map { resolvePersonName(it) }
        return NameMatcher.resolvePersonMatches(candidates, queryNames) { record ->
            record.category == MemoryCategory.BIRTHDAY.value && !record.eventDate.isNullOrBlank()
        }
    }

    private fun hasHomophoneOnlyMismatch(question: String, record: MemoryRecord): Boolean {
        val queryNames = NameMatcher.extractPersonNames(question).map { resolvePersonName(it) }
        if (queryNames.isEmpty()) return false
        val stored = DisambiguationHelper.displayPersonName(record)
        return queryNames.any { query ->
            query != stored && PinyinHelper.samePinyin(query, stored)
        }
    }

    private fun presentDisambiguation(records: List<MemoryRecord>, question: String) {
        if (records.isEmpty()) return
        val prompt = DisambiguationHelper.buildChoicePrompt(records, question)
        pendingDisambiguationRecords = records
        pendingDisambiguationQuestion = question
        followUpContext = FollowUpContext.QUERY
        syncFollowUpUiState()
        replaceLastStatusMessage(prompt)
        speakAssistantText(prompt)
    }

    private fun handleVagueDeleteRequest() {
        _uiState.update { it.copy(phase = AppPhase.PROCESSING, isListening = false) }
        viewModelScope.launch {
            val records = repository.getAll()
            if (records.isEmpty()) {
                val reply = "目前还没有记下任何内容，没有可删的。"
                addAssistantMessage(reply)
                speakAssistantText(reply)
                return@launch
            }
            val prompt = DeleteRequestHelper.buildDeleteChoicePrompt(records)
            pendingDisambiguationRecords = records
            pendingDisambiguationQuestion = null
            followUpContext = FollowUpContext.DELETE
            syncFollowUpUiState()
            addAssistantMessage(prompt)
            speakAssistantText(prompt)
        }
    }

    private fun handleDeleteDisambiguationChoice(text: String) {
        val records = pendingDisambiguationRecords ?: return

        if (DeleteRequestHelper.isDeleteCancellation(text)) {
            handleDeleteCancellation()
            return
        }

        _uiState.update {
            it.copy(phase = AppPhase.PROCESSING, isListening = false)
        }

        val index = DisambiguationHelper.parseChoiceIndex(text, records.size)
        if (index == null) {
            val retry = buildString {
                append("我没听清是第几个，请再说一次，比如：第一个，或者 2")
                append("\n")
                append(DeleteRequestHelper.buildDeleteChoicePrompt(records))
            }
            addAssistantMessage(retry)
            speakAssistantText(retry)
            return
        }

        val augmentedIntent = DeleteRequestHelper.buildAugmentedDeleteIntent(index, records[index])
        clearDisambiguation()
        replaceLastUserMessageText(augmentedIntent)
        processWithSecretary()
    }

    private fun handleDeleteCancellation() {
        clearDisambiguation()
        val reply = DeleteRequestHelper.buildDeleteCancellationReply()
        addAssistantMessage(reply)
        speakAssistantText(reply)
    }

    private fun clearDisambiguation() {
        pendingDisambiguationRecords = null
        pendingDisambiguationQuestion = null
        followUpContext = FollowUpContext.NONE
        pendingQueryQuestion = null
        syncFollowUpUiState()
    }

    private fun handleDisambiguationChoice(text: String) {
        val records = pendingDisambiguationRecords ?: return
        val question = pendingDisambiguationQuestion.orEmpty()

        if (looksLikeNewQuery(text)) {
            clearDisambiguation()
            queryMemory(text)
            return
        }

        _uiState.update {
            it.copy(phase = AppPhase.PROCESSING, isListening = false)
        }

        val index = DisambiguationHelper.parseChoiceIndex(text, records.size)
        if (index == null) {
            val retry = buildString {
                append("我没听清是第几个，请再说一次，比如：第一个，或者 2")
                append("\n")
                append(DisambiguationHelper.buildChoicePrompt(records, question))
            }
            addAssistantMessage(retry)
            speakAssistantText(retry)
            return
        }

        clearDisambiguation()
        val answer = DisambiguationHelper.answerForRecord(records[index], question)
        finishQueryResponse(answer)
    }

    private fun looksLikeNewQuery(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isEmpty()) return false
        return normalized.contains("生日") ||
            normalized.contains("几号") ||
            normalized.contains("哪天") ||
            normalized.contains("什么时候")
    }

    private fun buildDirectBirthdayAnswer(
        question: String,
        candidates: List<MemoryRecord>
    ): String? {
        val birthdayMatches = findBirthdayMatches(question, candidates)
        if (birthdayMatches.size != 1) return null

        val record = birthdayMatches.first()
        return DisambiguationHelper.answerForRecord(record, question)
    }

    private fun shouldSkipQueryFallback(plan: QueryPlanPayload): Boolean =
        plan.category.isNullOrBlank() && plan.keywords.isNullOrEmpty()

    private suspend fun resolveQueryCandidates(
        plan: QueryPlanPayload,
        category: MemoryCategory?,
        keywords: List<String>,
        question: String
    ): List<MemoryRecord> {
        if (shouldSkipQueryFallback(plan)) {
            return repository.searchForQuery(
                category = category,
                keywords = keywords,
                question = question,
                allowBroadFallback = true
            )
        }

        val primary = repository.searchForQuery(
            category = category,
            keywords = keywords,
            question = question,
            allowBroadFallback = false
        )
        if (primary.isNotEmpty()) return primary

        return fallbackSearchByAiMatch(question)
    }

    private suspend fun fallbackSearchByAiMatch(userQuery: String): List<MemoryRecord> {
        Log.i("LittleHelperDB", "Fallback triggered for query: $userQuery")
        val recent = repository.getRecentSummaries(limit = 50)
        if (recent.isEmpty()) return emptyList()

        val matchedIds = deepSeekService.matchRecords(userQuery, recent)
        Log.i(
            "LittleHelperDB",
            "Fallback candidateCount=${recent.size}, matchedCount=${matchedIds.size}"
        )
        return repository.getByIds(matchedIds)
    }

    private fun queryMemory(question: String, fromQueryFollowUp: Boolean = false) {

        viewModelScope.launch {

            try {

                if (!deepSeekService.hasApiKey()) {

                    setError("请先在 local.properties 配置 DEEPSEEK_API_KEY")

                    return@launch

                }

                val effectiveQuestion = when {
                    fromQueryFollowUp && !pendingQueryQuestion.isNullOrBlank() ->
                        "${pendingQueryQuestion}（用户补充：$question）"
                    else -> {
                        pendingQueryQuestion = question
                        question
                    }
                }

                addAssistantMessage("正在查找记录…")

                if (RecordListQueryHelper.isListAllRecordsQuestion(effectiveQuestion)) {
                    val allRecords = repository.getAll()
                    val answer = DisambiguationHelper.buildAllRecordsAnswer(allRecords)
                    finishQueryResponse(answer)
                    return@launch
                }

                val plan = deepSeekService.planQuery(effectiveQuestion)

                val category = plan.category?.let(MemoryCategory::fromValue)

                val resolvedNames = NameMatcher.extractPersonNames(effectiveQuestion).map { resolvePersonName(it) }
                val keywords = (plan.keywords.orEmpty() + resolvedNames).distinct()

                val candidates = resolveQueryCandidates(
                    plan = plan,
                    category = category,
                    keywords = keywords,
                    question = effectiveQuestion
                )

                val birthdayMatches = findBirthdayMatches(effectiveQuestion, candidates)
                if (birthdayMatches.size == 1 && hasHomophoneOnlyMismatch(effectiveQuestion, birthdayMatches.first())) {
                    presentDisambiguation(birthdayMatches, effectiveQuestion)
                    return@launch
                }
                if (birthdayMatches.size > 1) {
                    presentDisambiguation(birthdayMatches, effectiveQuestion)
                    return@launch
                }

                val directAnswer = buildDirectBirthdayAnswer(effectiveQuestion, candidates)
                if (directAnswer != null) {
                    finishQueryResponse(directAnswer)
                    return@launch
                }

                val personMatches = findPersonMatches(effectiveQuestion, candidates)
                if (personMatches.size > 1) {
                    presentDisambiguation(personMatches, effectiveQuestion)
                    return@launch
                }

                replaceLastStatusMessage("正在回答…")

                val answer = deepSeekService.answerQuery(effectiveQuestion, candidates)

                finishQueryResponse(answer)

            } catch (e: Exception) {

                setError(e.message ?: "查询失败，请稍后再试")

            }

        }

    }



    private fun noteSaveFollowUpInvitation(assistantText: String) {
        if (VoiceIntentDetector.invitesSaveFollowUp(assistantText)) {
            followUpContext = FollowUpContext.SAVE
            syncFollowUpUiState()
        }
    }

    private fun noteDeleteFollowUpInvitation(assistantText: String) {
        if (VoiceIntentDetector.asksDeleteDisambiguationChoice(assistantText)) {
            followUpContext = FollowUpContext.DELETE
            syncFollowUpUiState()
        }
    }

    /** 纯查询结束：清空跟进上下文，净化文案，TTS 期间保持 IDLE（按钮显示「按住 说话」）。 */
    private fun finishQueryResponse(answer: String) {
        pendingQueryQuestion = null
        pendingDisambiguationRecords = null
        pendingDisambiguationQuestion = null
        followUpContext = FollowUpContext.NONE
        syncFollowUpUiState()

        val sanitized = sanitizeQueryAnswer(answer)
        replaceLastStatusMessage(sanitized)
        speakAssistantText(
            text = sanitized,
            phaseWhileSpeaking = AppPhase.IDLE,
            onCompletePhase = AppPhase.IDLE
        )
    }

    /** 剥离查询回答里误带的「记下/保存」口癖（AI 或误路由时兜底）。 */
    private fun sanitizeQueryAnswer(answer: String): String {
        val trimmed = answer.trim()
        if (trimmed.isEmpty()) return trimmed

        val savePrefixPatterns = listOf(
            Regex("""^好的[，,]?已经记下\s*\d*\s*条[：:]\s*"""),
            Regex("""^好的[，,]?已经记下[：:]\s*"""),
            Regex("""^好的[，,]?已经帮您记好了[。.]?\s*"""),
            Regex("""^好的[，,]?已经记下了[：:]\s*""")
        )
        for (pattern in savePrefixPatterns) {
            if (pattern.containsMatchIn(trimmed)) {
                return trimmed.replaceFirst(pattern, "").trim()
            }
        }
        return trimmed
    }



    private fun setError(message: String) {

        addAssistantMessage(message, isError = true)

        _uiState.update {

            it.copy(phase = AppPhase.ERROR, isListening = false)

        }

        speakAssistantText(
            text = message,
            phaseWhileSpeaking = AppPhase.ERROR
        )

    }



    fun deleteChatMessage(messageId: String) {
        _uiState.update { state ->
            state.copy(messages = state.messages.filterNot { it.id == messageId })
        }
    }

    private fun addPartialUserMessage() {

        _uiState.update { state ->

            val withoutPartial = state.messages.filterNot { it.isPartial }

            state.copy(

                messages = withoutPartial + ChatMessage.user(text = "", isPartial = true)

            )

        }

    }



    private fun updatePartialUserMessage(text: String) {

        _uiState.update { state ->

            val messages = state.messages.toMutableList()

            val index = messages.indexOfLast { it.id == ChatMessage.PARTIAL_USER_ID }

            if (index >= 0) {

                messages[index] = ChatMessage.user(text = text, isPartial = true)

            } else {

                messages.add(ChatMessage.user(text = text, isPartial = true))

            }

            val action = detectAction(text)

            pendingAction = action

            state.copy(messages = messages)

        }

    }



    private fun finalizeUserMessage(text: String) {

        _uiState.update { state ->

            val messages = state.messages.toMutableList()

            val index = messages.indexOfLast { it.id == ChatMessage.PARTIAL_USER_ID }

            if (index >= 0) {

                if (text.isBlank()) {

                    messages.removeAt(index)

                } else {

                    messages[index] = ChatMessage.user(text = text, isPartial = false)

                }

            } else if (text.isNotBlank()) {

                messages.add(ChatMessage.user(text = text))

            }

            if (text.isNotBlank()) {

                userTurnsSinceLastSave++

            }

            pendingAction = detectAction(text)

            state.copy(messages = messages)

        }

    }



    private fun removePartialUserMessage() {

        _uiState.update { state ->

            state.copy(messages = state.messages.filterNot { it.isPartial })

        }

    }



    private fun addAssistantMessage(text: String, isError: Boolean = false) {

        _uiState.update { state ->

            state.copy(messages = state.messages + ChatMessage.assistant(text, isError))

        }

    }



    private fun replaceLastUserMessageText(text: String) {
        _uiState.update { state ->
            val messages = state.messages.toMutableList()
            val index = messages.indexOfLast { it.role == ChatRole.USER && !it.isPartial }
            if (index >= 0) {
                messages[index] = messages[index].copy(text = text)
            }
            state.copy(messages = messages)
        }
    }

    private fun replaceLastStatusMessage(text: String) {

        _uiState.update { state ->

            val messages = state.messages.toMutableList()

            val statusPhrases = listOf("正在整理要点", "正在查找记录", "正在回答", "正在更正记录", "正在思考")

            val index = messages.indexOfLast { message ->

                message.role == ChatRole.ASSISTANT &&

                    statusPhrases.any { phrase -> message.text.startsWith(phrase) }

            }

            if (index >= 0) {

                val existing = messages[index]

                messages[index] = existing.copy(text = text, isError = false)

            } else {

                messages.add(ChatMessage.assistant(text))

            }

            state.copy(messages = messages)

        }

    }



    private fun detectAction(text: String): VoiceAction {
        val lastAssistantMessage = _uiState.value.messages
            .filterNot { it.isPartial || it.isError }
            .lastOrNull { it.role == ChatRole.ASSISTANT }
            ?.text
        return VoiceIntentDetector.detect(text, lastAssistantMessage, followUpContext)
    }

    private fun speakAssistantText(
        text: String,
        phaseWhileSpeaking: AppPhase = AppPhase.ANSWERING,
        onCompletePhase: AppPhase = AppPhase.IDLE
    ) {
        if (text.isBlank()) {
            _uiState.update { it.copy(phase = onCompletePhase, speakingMessageId = null) }
            return
        }

        val messageId = _uiState.value.messages.lastOrNull { it.role == ChatRole.ASSISTANT }?.id

        _uiState.update {
            it.copy(phase = phaseWhileSpeaking, speakingMessageId = messageId)
        }

        ttsManager?.speak(text) {
            _uiState.update { state ->
                state.copy(phase = onCompletePhase, speakingMessageId = null)
            }
        } ?: _uiState.update { it.copy(phase = onCompletePhase, speakingMessageId = null) }
    }

    /**
     * 清洗「失败轮」：某轮 user 消息后紧跟的 assistant 消息是 isError（网络超时/断开导致本轮请求未得到
     * 有效回复），这条孤儿 user 消息会干扰下一轮的对话基准（如 AI 把废话/"timeout" 前的输入误当上下文）。
     * 此函数将「user + 紧随其后的 error assistant」配对从准备送往 API 的历史中一并剔除。
     * 说明：_uiState.messages（UI 层）不受影响，用户仍能看到红色错误提示；只清洗送 API 的 history。
     */
    private fun List<ChatMessage>.withoutFailedTurns(): List<ChatMessage> {
        if (size < 2) return this
        val result = toMutableList()
        var i = result.size - 1
        while (i > 0) {
            val curr = result[i]
            val prev = result[i - 1]
            if (curr.isError && curr.role == ChatRole.ASSISTANT && prev.role == ChatRole.USER) {
                result.removeAt(i)
                result.removeAt(i - 1)
                i -= 2
            } else {
                i--
            }
        }
        return result
    }

}


