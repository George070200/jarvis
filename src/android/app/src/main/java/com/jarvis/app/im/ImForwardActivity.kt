package com.jarvis.app.im

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.app.MinisApp
import com.jarvis.app.agent.RoleStore
import com.jarvis.app.ui.chat.ChatViewModelStore
import com.jarvis.app.ui.theme.MinisTheme
import kotlinx.coroutines.launch

/**
 * [T-im-forward] IM「贾维斯」转发的落点选择器。
 *
 * 文本由 [ImFlutterActivity] 的 `forwardToAgent` 通道送来（finish IM 后启动
 * 本 Activity）。选择 Agent 角色 → 该角色的「新建会话」或某个历史会话 →
 * 可编辑附言 → 把转发文本注入为目标会话的一条 user 行（附言单独一行），
 * 并借用 [ChatViewModelStore.stashPendingForwardRespond] 让目标会话打开后
 * 自动开始 agent 回合。
 *
 * 复用应用级 MinisApp.chatRepository，与会话内「转发到角色会话」共用同一
 * 套拷贝/注入语义（见 ChatViewModel.forwardMessagesToRoleInternal）。
 */
class ImForwardActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TEXT = "jarvis.im.forward_text"

        fun start(context: Context, text: String) {
            context.startActivity(
                Intent(context, ImForwardActivity::class.java).apply {
                    putExtra(EXTRA_TEXT, text)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                },
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(EXTRA_TEXT).orEmpty()
        if (text.isBlank()) {
            finish()
            return
        }
        setContent {
            MinisTheme {
                ImForwardPickerScreen(text = text, onDone = { finish() })
            }
        }
    }
}

@Composable
private fun ImForwardPickerScreen(text: String, onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as MinisApp
    val roles by RoleStore.rolesSnapshot.collectAsState()
    val allSessions by app.chatRepository.observeSessions()
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var chosenRoleId by remember { mutableStateOf<String?>(null) }
    var targetSessionId by remember { mutableStateOf<String?>(null) }
    var targetPicked by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var forwarding by remember { mutableStateOf(false) }

    val chosenRole = chosenRoleId?.let { id -> roles.firstOrNull { it.roleId == id } }
    val roleSessions = allSessions.filter { it.roleId == chosenRoleId }
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            // imePadding：附言输入框聚焦时整体抬到键盘上方，转发按钮不被遮挡。
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!targetPicked) {
                Text(
                    text = if (chosenRole == null) {
                        "选择角色"
                    } else {
                        "选择目标（${chosenRole.metadata.name}）"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (chosenRole == null) {
                        items(roles, key = { it.roleId }) { role ->
                            Text(
                                text = role.metadata.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { chosenRoleId = role.roleId }
                                    .padding(vertical = 14.dp),
                            )
                        }
                    } else {
                        item(key = "__new__") {
                            Text(
                                text = "新建会话",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        targetSessionId = null
                                        targetPicked = true
                                    }
                                    .padding(vertical = 14.dp),
                            )
                        }
                        items(roleSessions, key = { it.id }) { session ->
                            Text(
                                text = session.title?.takeIf { it.isNotBlank() }
                                    ?: "会话 ${session.id.take(8)}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        targetSessionId = session.id
                                        targetPicked = true
                                    }
                                    .padding(vertical = 14.dp),
                            )
                        }
                    }
                }
            } else {
                Text(text = "转发草稿（可编辑附言）", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("附言（可选）") },
                    minLines = 2,
                )
                Button(
                    enabled = !forwarding,
                    onClick = {
                        scope.launch {
                            forwarding = true
                            try {
                                val repo = app.chatRepository
                                val existing = targetSessionId?.let { repo.getSession(it) }
                                val sessionId = if (existing != null) {
                                    existing.id
                                } else {
                                    val modelId = app.providerRepository
                                        .allVisibleEntries()
                                        .firstOrNull()?.model?.id ?: "unknown"
                                    repo.createSession(
                                        modelId = modelId,
                                        title = text.replace(Regex("\\s+"), " ").take(50),
                                        roleId = chosenRoleId,
                                    ).id
                                }
                                val parts = org.json.JSONArray()
                                    .put(
                                        org.json.JSONObject()
                                            .put("type", "text")
                                            .put("value", text),
                                    )
                                    .toString()
                                repo.appendMessage(sessionId, "user", parts, roleId = null)
                                val trimmedNote = note.trim()
                                if (trimmedNote.isNotEmpty()) {
                                    val noteParts = org.json.JSONArray()
                                        .put(
                                            org.json.JSONObject()
                                                .put("type", "text")
                                                .put("value", trimmedNote),
                                        )
                                        .toString()
                                    repo.appendMessage(sessionId, "user", noteParts, roleId = null)
                                }
                                ChatViewModelStore.stashPendingForwardRespond(
                                    ChatViewModelStore.PendingForwardRespond(targetId = sessionId),
                                )
                                // 转发到已有会话时，可能有缓存的空闲 VM 还拿着
                                // 旧历史——不释放的话，刚追加的转发行不可见
                                // （与会话内 forwardMessagesToRoleInternal 同语义）。
                                if (existing != null) {
                                    ChatViewModelStore.release(sessionId)
                                }
                                // 转发完成后直接进入目标会话：经深链让 MainActivity
                                // 导航到该会话，避免停留在会话列表或旧界面。
                                com.jarvis.app.logging.AppLogger.info(
                                    "ImForward",
                                    "deep link → jarvis://session/$sessionId",
                                )
                                android.util.Log.i("ImForward", "deep link → jarvis://session/$sessionId")
                                context.startActivity(
                                    android.content.Intent(context, com.jarvis.app.MainActivity::class.java).apply {
                                        data = android.net.Uri.parse("jarvis://session/$sessionId")
                                        addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        )
                                    },
                                )
                                forwarding = false
                                onDone()
                            } catch (e: Exception) {
                                forwarding = false
                                android.widget.Toast.makeText(
                                    context,
                                    "转发失败：${e.message}",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("转发")
                }
                Text(
                    text = "返回",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { targetPicked = false }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}
