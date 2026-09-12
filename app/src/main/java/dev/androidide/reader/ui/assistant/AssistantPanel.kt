package dev.androidide.reader.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.androidide.reader.domain.ai.ChatRole

@Composable
fun AssistantPanel(viewModel: AssistantViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPath by remember { mutableStateOf(false) }
    var path by remember { mutableStateOf("") }
    DisposableEffect(viewModel) { onDispose(viewModel::onPageStopped) }

    Column(modifier.fillMaxHeight().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("AI 助手", style = MaterialTheme.typography.titleLarge)
            Row {
                IconButton(onClick = viewModel::retry, enabled = !state.isGenerating) {
                    Icon(Icons.Default.Refresh, "重试")
                }
                IconButton(onClick = viewModel::clearConversation) {
                    Icon(Icons.Default.Clear, "清空会话")
                }
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.messages.isEmpty()) item { Text("询问代码问题，或先附加所需上下文。", Modifier.padding(vertical = 20.dp)) }
            items(state.messages, key = { it.id }) { message ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(if (message.role == ChatRole.USER) "你" else "助手", style = MaterialTheme.typography.labelMedium)
                        Text(message.content.ifEmpty { "…" }, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ContextPreviewCard(state, viewModel::removeAttachment)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = viewModel::attachCurrentFile, label = { Text("当前文件") }, leadingIcon = { Icon(Icons.Default.Add, null) })
            AssistChip(onClick = viewModel::attachSelection, label = { Text("选中代码") })
            AssistChip(onClick = { showPath = !showPath }, label = { Text("指定文件") })
        }
        if (showPath) Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(path, { path = it }, Modifier.weight(1f), label = { Text("项目内文件路径") }, singleLine = true)
            Button(onClick = { viewModel.attachFile(path); path = ""; showPath = false }, Modifier.padding(start = 6.dp, top = 8.dp)) { Text("附加") }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = viewModel::updateDraft,
                modifier = Modifier.weight(1f),
                label = { Text("输入问题") },
                maxLines = 5,
            )
            IconButton(onClick = if (state.isGenerating) viewModel::stopGenerating else viewModel::send) {
                Icon(if (state.isGenerating) Icons.Default.Stop else Icons.Default.Send, if (state.isGenerating) "停止生成" else "发送")
            }
        }
    }
}

@Composable
private fun ContextPreviewCard(state: AssistantUiState, remove: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(8.dp)) {
            Text("即将发送的上下文 · ${formatBytes(state.contextPreview.estimatedBytes)} / ${formatBytes(state.contextPreview.maxTotalBytes)}")
            if (state.attachments.isEmpty()) Text("未附加文件（不会上传整个项目）", style = MaterialTheme.typography.bodySmall)
            state.attachments.forEachIndexed { index, attachment ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${attachment.path} · ${formatBytes(attachment.estimatedBytes)}", Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { remove(index) }) { Icon(Icons.Default.Close, "移除附件") }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Int): String = if (bytes < 1024) "$bytes B" else "%.1f KiB".format(bytes / 1024f)
