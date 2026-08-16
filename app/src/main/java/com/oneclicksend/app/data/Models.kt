package com.oneclicksend.app.data

import com.oneclicksend.app.BuildConfig

enum class Messenger {
    TELEGRAM,
    VK,
}

data class AppSettings(
    val messenger: Messenger? = null,
    val telegramToken: String = "",
    val vkGroupToken: String = "",
    val chatId: String = "",
    val chatTitle: String = "",
    val configured: Boolean = false,
) {
    val isReadyToSend: Boolean
        get() = configured && messenger != null && chatId.isNotBlank() && tokenFor(messenger).isNotBlank()

    fun tokenFor(messenger: Messenger?): String {
        return when (messenger) {
            Messenger.TELEGRAM -> telegramToken.ifBlank { BuildConfig.TELEGRAM_BOT_TOKEN }
            Messenger.VK -> vkGroupToken.ifBlank { BuildConfig.VK_GROUP_TOKEN }
            null -> ""
        }
    }
}

data class ChatCandidate(
    val id: String,
    val title: String,
    val subtitle: String = "",
)
