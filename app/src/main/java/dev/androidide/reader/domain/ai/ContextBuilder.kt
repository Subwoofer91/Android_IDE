package dev.androidide.reader.domain.ai

data class ContextLimits(
    val maxFileBytes: Int = 128 * 1024,
    val maxTotalBytes: Int = 512 * 1024,
)

data class ContextPreview(
    val attachments: List<ContextAttachment>,
    val estimatedBytes: Int,
    val maxTotalBytes: Int,
)

/** Builds context exclusively from explicit attachments; project-wide discovery is intentionally absent. */
class ContextBuilder(private val limits: ContextLimits = ContextLimits()) {
    fun build(explicitAttachments: List<ContextAttachment>): ContextPreview {
        explicitAttachments.forEach { attachment ->
            if (attachment.estimatedBytes > limits.maxFileBytes) {
                throw AiError.ContextTooLarge(
                    "${attachment.path} 超过单文件限制 ${limits.maxFileBytes} B",
                )
            }
        }
        val total = explicitAttachments.sumOf(ContextAttachment::estimatedBytes)
        if (total > limits.maxTotalBytes) {
            throw AiError.ContextTooLarge("上下文 ${total} B 超过总限制 ${limits.maxTotalBytes} B")
        }
        return ContextPreview(explicitAttachments.toList(), total, limits.maxTotalBytes)
    }
}
