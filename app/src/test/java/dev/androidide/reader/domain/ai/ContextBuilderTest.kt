package dev.androidide.reader.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ContextBuilderTest {
    @Test
    fun `build includes only explicit files and reports utf8 size`() {
        val attachment = ContextAttachment("A.kt", "你好", AttachmentKind.FILE)
        val preview = ContextBuilder(ContextLimits(maxFileBytes = 8, maxTotalBytes = 8)).build(listOf(attachment))

        assertEquals(listOf(attachment), preview.attachments)
        assertEquals(6, preview.estimatedBytes)
    }

    @Test
    fun `single attachment limit is enforced`() {
        assertThrows(AiError.ContextTooLarge::class.java) {
            ContextBuilder(ContextLimits(maxFileBytes = 2, maxTotalBytes = 20))
                .build(listOf(ContextAttachment("large.kt", "123")))
        }
    }

    @Test
    fun `total attachment limit is enforced`() {
        assertThrows(AiError.ContextTooLarge::class.java) {
            ContextBuilder(ContextLimits(maxFileBytes = 10, maxTotalBytes = 5)).build(
                listOf(ContextAttachment("a", "123"), ContextAttachment("b", "456")),
            )
        }
    }
}
