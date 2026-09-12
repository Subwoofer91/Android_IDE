package dev.androidide.reader

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.androidide.reader.domain.ai.AiProvider
import dev.androidide.reader.domain.ai.StreamResponse
import dev.androidide.reader.domain.workspace.EditorSnapshot
import dev.androidide.reader.domain.workspace.WorkspaceFileSource
import dev.androidide.reader.ui.assistant.AssistantPanel
import dev.androidide.reader.ui.assistant.AssistantViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fileSource = DemoWorkspaceFileSource()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                val assistant: AssistantViewModel = viewModel(factory = assistantFactory(DemoAiProvider(), fileSource))
                ReaderScreen(assistant, fileSource.current()?.content.orEmpty())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(assistant: AssistantViewModel, document: String) {
    var showAssistant by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fixedTwoPane = maxWidth >= 600.dp || landscape
        Scaffold(
            bottomBar = {
                AnimatedVisibility(!fixedTwoPane) {
                    Button(
                        onClick = { showAssistant = true },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.AutoAwesome, null)
                        Text(" 打开 AI 助手")
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                ReadingPane(document, Modifier.weight(if (fixedTwoPane) 0.58f else 1f))
                if (fixedTwoPane) {
                    VerticalDivider()
                    AssistantPanel(assistant, Modifier.weight(0.42f))
                }
            }
        }
        if (!fixedTwoPane && showAssistant) {
            ModalBottomSheet(onDismissRequest = { showAssistant = false }) {
                AssistantPanel(assistant, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ReadingPane(document: String, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surface).padding(20.dp)) {
        Text("MainActivity.kt", style = MaterialTheme.typography.titleLarge)
        Text("app/src/main/java/dev/androidide/reader/MainActivity.kt", style = MaterialTheme.typography.labelSmall)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Box(Modifier.verticalScroll(rememberScrollState())) {
            Text(document, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun assistantFactory(provider: AiProvider, files: WorkspaceFileSource) =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AssistantViewModel(provider, files) as T
    }

private class DemoWorkspaceFileSource : WorkspaceFileSource {
    private val sample = """
        @Composable
        fun Greeting(name: String) {
            Text("Hello, ${'$'}name!")
        }

        // 选中一段代码后，可将它作为最小必要上下文发送给助手。
    """.trimIndent()

    override fun current() = EditorSnapshot("MainActivity.kt", sample, 0..42)
    override suspend fun read(path: String): String? = when (path.trim()) {
        "MainActivity.kt" -> sample
        else -> null
    }
}

private class DemoAiProvider : AiProvider {
    private val cancelled = mutableSetOf<String>()

    override fun stream(request: dev.androidide.reader.domain.ai.ChatRequest): Flow<StreamResponse> = flow {
        val response = "我已收到问题和 ${request.attachments.size} 个显式附件。这里可以接入任意供应商实现。"
        response.chunked(5).forEach { chunk ->
            if (request.requestId in cancelled) return@flow
            delay(80)
            emit(StreamResponse.TextDelta(chunk))
        }
        emit(StreamResponse.Completed())
    }

    override fun cancel(requestId: String) {
        cancelled += requestId
    }
}
