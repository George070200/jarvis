package com.jarvis.app.deeplink

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds pending deep-link side-effects that outlive a single navigation event.
 * Mirrors iOS DeepLinkCoordinator.
 */
object DeepLinkCoordinator {

    data class EnvVarCreate(val key: String, val value: String, val note: String)

    private val _pendingEnvVarCreate = MutableStateFlow<EnvVarCreate?>(null)
    val pendingEnvVarCreate: StateFlow<EnvVarCreate?> = _pendingEnvVarCreate.asStateFlow()

    fun setPendingEnvVarCreate(key: String, value: String, note: String = "") {
        _pendingEnvVarCreate.value = EnvVarCreate(key, value, note)
    }

    fun consumePendingEnvVarCreate(): EnvVarCreate? {
        val current = _pendingEnvVarCreate.value
        _pendingEnvVarCreate.value = null
        return current
    }

    /**
     * Optional `?tab=…` hint from `jarvis://settings/logs?tab=config-audit`.
     * The Logs screen reads this on appear to land on the right
     * segmented-control tab. Cleared by the screen after consumption.
     * Mirrors iOS DeepLinkCoordinator.pendingLogsTab.
     */
    private val _pendingLogsTab = MutableStateFlow<String?>(null)
    val pendingLogsTab: StateFlow<String?> = _pendingLogsTab.asStateFlow()

    fun setPendingLogsTab(tab: String?) { _pendingLogsTab.value = tab }
    fun consumePendingLogsTab(): String? {
        val current = _pendingLogsTab.value
        _pendingLogsTab.value = null
        return current
    }

    /**
     * [T-im-forward] 转发完成后要打开的目标会话。用进程级 StateFlow 而非
     * Activity 字段/意图传递：ImForwardActivity startActivity 后立刻 finish，
     * 任务切换期间 MainActivity 可能被重建，实例字段会随重建丢失、
     * Activity 时机导航的结果也会被丢弃。组合层收集此 flow 导航，
     * 重建后会重新收集并重放导航。带 2 分钟时效防陈旧跳转。
     */
    private val _pendingSessionOpen = MutableStateFlow<Pair<String, Long>?>(null)
    val pendingSessionOpen: StateFlow<Pair<String, Long>?> = _pendingSessionOpen.asStateFlow()

    fun setPendingSessionOpen(sessionId: String) {
        _pendingSessionOpen.value = sessionId to System.currentTimeMillis()
    }

    /** 挂起值是否仍然新鲜（2 分钟内）；过期即清除并返回 null。 */
    fun freshPendingSessionOpen(): String? {
        val current = _pendingSessionOpen.value ?: return null
        return if (System.currentTimeMillis() - current.second < 2 * 60_000) {
            current.first
        } else {
            _pendingSessionOpen.value = null
            null
        }
    }

    /**
     * Pending pinned-shortcut HTML preview: filesystem path + cached title.
     * MainActivity sets this on `jarvis://preview/html` deep link; ChatScreen
     * reads it on first composition and routes into WebPreviewFullscreen.
     */
    data class HtmlPreview(val sessionId: String, val resourcePath: String, val title: String)

    private val _pendingHtmlPreview = MutableStateFlow<HtmlPreview?>(null)
    val pendingHtmlPreview: StateFlow<HtmlPreview?> = _pendingHtmlPreview.asStateFlow()

    fun setPendingHtmlPreview(sessionId: String, resourcePath: String, title: String) {
        _pendingHtmlPreview.value = HtmlPreview(sessionId, resourcePath, title)
    }

    fun consumePendingHtmlPreview(): HtmlPreview? {
        val current = _pendingHtmlPreview.value
        _pendingHtmlPreview.value = null
        return current
    }

    /**
     * App-icon quick-action that a freshly-opened ChatScreen should auto-
     * trigger on first compose. Mirrors iOS `pendingChatAction` on
     * AIChatViewModel. Set by [com.jarvis.app.MainActivity] /
     * [com.jarvis.app.ui.navigation.AppNavigation] when the launch
     * intent carries `jarvis://action/voice_chat` or
     * `jarvis://action/camera_chat`; consumed exactly once by ChatScreen
     * so re-entering the same chat later doesn't fire the action again.
     */
    enum class ChatAction { START_VOICE, OPEN_CAMERA }

    private val _pendingChatAction = MutableStateFlow<ChatAction?>(null)
    val pendingChatAction: StateFlow<ChatAction?> = _pendingChatAction.asStateFlow()

    fun setPendingChatAction(action: ChatAction) {
        _pendingChatAction.value = action
    }

    fun consumePendingChatAction(): ChatAction? {
        val current = _pendingChatAction.value
        _pendingChatAction.value = null
        return current
    }
}
