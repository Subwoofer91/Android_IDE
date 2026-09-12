package dev.androidide.reader.domain.workspace

data class EditorSnapshot(
    val path: String,
    val content: String,
    val selection: IntRange? = null,
)

interface WorkspaceFileSource {
    fun current(): EditorSnapshot?
    suspend fun read(path: String): String?
}
