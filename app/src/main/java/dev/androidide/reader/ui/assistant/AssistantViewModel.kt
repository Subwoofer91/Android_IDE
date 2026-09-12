package dev.androidide.reader.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.androidide.reader.domain.ai.AiError
import dev.androidide.reader.domain.ai.AiProvider
import dev.androidide.reader.domain.ai.AttachmentKind
import dev.androidide.reader.domain.ai.ChatMessage
import dev.androidide.reader.domain.ai.ChatRequest
import dev.androidide.reader.domain.ai.ChatRole
import dev.androidide.reader.domain.ai.ContextAttachment
import dev.androidide.reader.domain.ai.ContextBuilder
import dev.androidide.reader.domain.ai.ContextPreview
import dev.androidide.reader.domain.ai.StreamResponse
import dev.androidide.reader.domain.workspace.WorkspaceFileSource
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssistantUiState(
    val sessionId: String = UUID.randomUUID().toString(),
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val attachments: List<ContextAttachment> = emptyList(),
    val contextPreview: ContextPreview = ContextPreview(emptyList(), 0, 512 * 1024),
    val isGenerating: Boolean = false,
    val error: String? = null,
)

class AssistantViewModel(
    private val provider: AiProvider,
    private val files: WorkspaceFileSource,
    private val contextBuilder: ContextBuilder = ContextBuilder(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = mutableState.asStateFlow()
    private var requestJob: Job? = null
    private var activeRequestId: String? = null

    fun updateDraft(value: String) = mutableState.update { it.copy(draft = value, error = null) }

    fun attachCurrentFile() {
        val snapshot = files.current() ?: return showError("当前没有打开的文件")
        addAttachment(ContextAttachment(snapshot.path, snapshot.content, AttachmentKind.CURRENT_FILE))
    }

    fun attachSelection() {
        val snapshot = files.current() ?: return showError("当前没有打开的文件")
        val range = snapshot.selection ?: return showError("请先选择代码")
        val safeRange = range.first.coerceAtLeast(0)..range.last.coerceAtMost(snapshot.content.lastIndex)
        if (safeRange.isEmpty()) return showError("选中内容为空")
        addAttachment(
            ContextAttachment(snapshot.path, snapshot.content.substring(safeRange), AttachmentKind.SELECTION, safeRange),
        )
    }

    fun attachFile(path: String) {
        if (path.isBlank()) return showError("请输入文件路径")
        viewModelScope.launch {
            val content = files.read(path) ?: return@launch showError("无法读取 $path")
            addAttachment(ContextAttachment(path, content, AttachmentKind.FILE))
        }
    }

    fun removeAttachment(index: Int) = updateAttachments {
        filterIndexed { itemIndex, _ -> itemIndex != index }
    }

    private fun addAttachment(attachment: ContextAttachment) = updateAttachments {
        filterNot { it.path == attachment.path && it.kind == attachment.kind } + attachment
    }

    private fun updateAttachments(transform: List<ContextAttachment>.() -> List<ContextAttachment>) {
        val attachments = mutableState.value.attachments.transform()
        runCatching { contextBuilder.build(attachments) }
            .onSuccess { preview -> mutableState.update { it.copy(attachments = attachments, contextPreview = preview, error = null) } }
            .onFailure { showError(it.message ?: "上下文无效") }
    }

    fun send() {
        val snapshot = mutableState.value
        val text = snapshot.draft.trim()
        if (text.isEmpty() || snapshot.isGenerating) return
        startRequest(text, snapshot.attachments)
    }

    fun retry() {
        if (mutableState.value.isGenerating) return
        val lastUser = mutableState.value.messages.lastOrNull { it.role == ChatRole.USER } ?: return
        mutableState.update { state ->
            val userIndex = state.messages.indexOfLast { it.id == lastUser.id }
            state.copy(messages = state.messages.take(userIndex), error = null)
        }
        startRequest(lastUser.content, lastUser.attachments)
    }

    private fun startRequest(text: String, attachments: List<ContextAttachment>) {
        val preview = try {
            contextBuilder.build(attachments)
        } catch (error: AiError.ContextTooLarge) {
            return showError(error.message ?: "上下文过大")
        }
        stopGenerating()
        val requestId = UUID.randomUUID().toString()
        activeRequestId = requestId
        val user = ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, text, attachments)
        val assistant = ChatMessage(UUID.randomUUID().toString(), ChatRole.ASSISTANT, "")
        val history = mutableState.value.messages + user
        mutableState.update {
            it.copy(
                messages = history + assistant,
                draft = "",
                attachments = emptyList(),
                contextPreview = contextBuilder.build(emptyList()),
                isGenerating = true,
                error = null,
            )
        }
        requestJob = viewModelScope.launch {
            provider.stream(ChatRequest(requestId, history, preview.attachments))
                .catch { error ->
                    if (error !is CancellationException) showError(error.message ?: "生成失败")
                }
                .collect { response ->
                    when (response) {
                        is StreamResponse.TextDelta -> mutableState.update { state ->
                            state.copy(messages = state.messages.map {
                                if (it.id == assistant.id) it.copy(content = it.content + response.text) else it
                            })
                        }
                        is StreamResponse.Completed -> Unit
                    }
                }
            mutableState.update { it.copy(isGenerating = false) }
            activeRequestId = null
        }
    }

    fun stopGenerating() {
        activeRequestId?.let(provider::cancel)
        requestJob?.cancel()
        requestJob = null
        activeRequestId = null
        mutableState.update { it.copy(isGenerating = false) }
    }

    fun clearConversation() {
        stopGenerating()
        mutableState.value = AssistantUiState()
    }

    fun switchSession(sessionId: String) {
        if (sessionId == mutableState.value.sessionId) return
        stopGenerating()
        mutableState.value = AssistantUiState(sessionId = sessionId)
    }

    /** Called by the hosting page when it leaves composition. */
    fun onPageStopped() = stopGenerating()

    private fun showError(message: String) = mutableState.update { it.copy(error = message, isGenerating = false) }

    override fun onCleared() {
        stopGenerating()
        super.onCleared()
    }
}
