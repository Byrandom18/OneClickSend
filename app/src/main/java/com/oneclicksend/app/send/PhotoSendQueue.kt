package com.oneclicksend.app.send

import com.oneclicksend.app.data.Messenger
import com.oneclicksend.app.data.SettingsRepository
import com.oneclicksend.app.network.TelegramClient
import com.oneclicksend.app.network.VkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

data class SendUiState(
    val pendingCount: Int = 0,
    val lastStatus: String? = null,
    val lastError: String? = null,
    val lastSuccess: Boolean = false,
)

class PhotoSendQueue(
    private val settingsRepository: SettingsRepository,
    private val telegramClient: TelegramClient,
    private val vkClient: VkClient,
) {
    private val jobs = Channel<File>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(SendUiState())
    val state: StateFlow<SendUiState> = _state.asStateFlow()

    init {
        scope.launch {
            for (file in jobs) {
                sendFile(file)
            }
        }
    }

    fun enqueue(file: File) {
        _state.update {
            it.copy(
                pendingCount = it.pendingCount + 1,
                lastStatus = "Отправка…",
                lastError = null,
                lastSuccess = false,
            )
        }
        jobs.trySend(file)
    }

    private suspend fun sendFile(file: File) {
        try {
            val settings = settingsRepository.current()
            if (!settings.isReadyToSend) {
                throw IllegalStateException("Чат ещё не выбран")
            }
            val token = settings.tokenFor(settings.messenger)
            if (token.isBlank()) {
                throw IllegalStateException("Нет ключа. Откройте «Сменить чат» и вставьте токен.")
            }
            when (settings.messenger) {
                Messenger.TELEGRAM -> telegramClient.sendPhoto(token, settings.chatId, file)
                Messenger.VK -> vkClient.sendPhoto(token, settings.chatId, file)
                null -> throw IllegalStateException("Мессенджер не выбран")
            }
            _state.update {
                it.copy(
                    pendingCount = (it.pendingCount - 1).coerceAtLeast(0),
                    lastStatus = "Отправлено",
                    lastError = null,
                    lastSuccess = true,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    pendingCount = (it.pendingCount - 1).coerceAtLeast(0),
                    lastStatus = "Не удалось отправить",
                    lastError = error.message ?: "Неизвестная ошибка",
                    lastSuccess = false,
                )
            }
        } finally {
            file.delete()
        }
    }
}
