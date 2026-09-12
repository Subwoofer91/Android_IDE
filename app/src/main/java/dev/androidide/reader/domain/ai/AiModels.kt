package dev.androidide.reader.domain.ai

import kotlinx.coroutines.flow.Flow

/** A provider-neutral contract. Implementations own authentication and transport details. */
interface AiProvider {
    fun stream(request: ChatRequest): Flow<StreamResponse>
    fun cancel(requestId: String)
}

enum class ChatRole { SYSTEM, USER, ASSISTANT }

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    val attachments: List<ContextAttachment> = emptyList(),
)

data class ChatRequest(
    val requestId: String,
    val messages: List<ChatMessage>,
    val attachments: List<ContextAttachment>,
)

data class ContextAttachment(
    val path: String,
    val content: String,
    val kind: AttachmentKind = AttachmentKind.FILE,
    val range: IntRange? = null,
) {
    val estimatedBytes: Int get() = content.toByteArray(Charsets.UTF_8).size
}

enum class AttachmentKind { CURRENT_FILE, SELECTION, FILE }

sealed interface StreamResponse {
    data class TextDelta(val text: String) : StreamResponse
    data class Completed(val usage: TokenUsage? = null) : StreamResponse
}

data class TokenUsage(val inputTokens: Int, val outputTokens: Int)

sealed class AiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Authentication(message: String = "AI 服务认证失败") : AiError(message)
    class RateLimited(val retryAfterMillis: Long? = null) : AiError("请求过于频繁")
    class ContextTooLarge(message: String) : AiError(message)
    class Network(cause: Throwable) : AiError("网络连接失败", cause)
    class Provider(message: String, cause: Throwable? = null) : AiError(message, cause)
    data object Cancelled : AiError("请求已取消")
}

fun interface CancellationRequest {
    fun cancel(requestId: String)
}
