package com.jarvis.app.data.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * 按语言取模型显示名。
 *
 * 网关档位会把中英文名一起同步进 [LLMModel]（displayName / displayNameEn），
 * 这里在展示时按当前 app 语言选择，而不是在同步时写死一种语言——这样在
 * 设置里切换语言后无需联网重新同步。
 *
 * 中文（zh*）用 displayName；其他语言优先 displayNameEn，缺失时回退 displayName。
 */
fun LLMModel.localizedDisplayName(locale: Locale): String {
    val en = displayNameEn?.takeIf { it.isNotBlank() } ?: displayName
    return if (locale.language.startsWith("zh", ignoreCase = true)) displayName else en
}

/** Compose 场景的便捷版本：语言取自当前 Composition 的 Configuration。 */
@Composable
fun LLMModel.localizedName(): String {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    return localizedDisplayName(locale)
}