package com.oneclicksend.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oneclicksend.app.AppContainer
import com.oneclicksend.app.data.AppSettings
import com.oneclicksend.app.data.ChatCandidate
import com.oneclicksend.app.data.Messenger
import com.oneclicksend.app.data.SettingsRepository
import com.oneclicksend.app.network.TelegramClient
import com.oneclicksend.app.network.VkClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SetupStep {
    MESSENGER,
    TOKEN,
    CHAT,
}

data class SetupUiState(
    val step: SetupStep = SetupStep.MESSENGER,
    val messenger: Messenger? = null,
    val token: String = "",
    val accountName: String = "",
    val chats: List<ChatCandidate> = emptyList(),
    val selectedChat: ChatCandidate? = null,
    val manualId: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val missingToken: Boolean = false,
)

class SetupViewModel(
    private val settingsRepository: SettingsRepository,
    private val telegramClient: TelegramClient,
    private val vkClient: VkClient,
) : ViewModel() {

    private var loaded = false
    private var storedSettings = AppSettings()

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    fun loadFrom(settings: AppSettings) {
        storedSettings = settings
        if (loaded) return
        loaded = true
        val messenger = settings.messenger
        _state.update {
            it.copy(
                messenger = messenger,
                token = settings.tokenFor(messenger),
                selectedChat = settings.chatId.takeIf { id -> id.isNotBlank() }?.let { id ->
                    ChatCandidate(id = id, title = settings.chatTitle.ifBlank { id })
                },
                manualId = settings.chatId,
                step = when {
                    !settings.configured || messenger == null -> SetupStep.MESSENGER
                    settings.tokenFor(messenger).isBlank() -> SetupStep.TOKEN
                    else -> SetupStep.CHAT
                },
            )
        }
        if (_state.value.step == SetupStep.CHAT) {
            refreshChats()
        }
    }

    fun selectMessenger(messenger: Messenger) {
        _state.update {
            it.copy(
                messenger = messenger,
                token = storedSettings.tokenFor(messenger),
                error = null,
                missingToken = false,
                chats = emptyList(),
                accountName = "",
            )
        }
    }

    fun onTokenChange(value: String) {
        _state.update { it.copy(token = value, error = null, missingToken = false) }
    }

    fun onManualIdChange(value: String) {
        _state.update { it.copy(manualId = value, error = null) }
    }

    fun selectChat(chat: ChatCandidate) {
        _state.update { it.copy(selectedChat = chat, manualId = chat.id, error = null) }
    }

    fun goNext() {
        val current = _state.value
        when (current.step) {
            SetupStep.MESSENGER -> {
                if (current.messenger == null) {
                    _state.update { it.copy(error = "Выберите, куда отправлять") }
                    return
                }
                _state.update { it.copy(step = SetupStep.TOKEN, error = null) }
            }
            SetupStep.TOKEN -> checkTokenAndOpenChats()
            SetupStep.CHAT -> save()
        }
    }

    fun goBack() {
        _state.update { current ->
            when (current.step) {
                SetupStep.CHAT -> current.copy(step = SetupStep.TOKEN, error = null, missingToken = false)
                SetupStep.TOKEN -> current.copy(step = SetupStep.MESSENGER, error = null, missingToken = false)
                SetupStep.MESSENGER -> current
            }
        }
    }

    fun openChatPicker() {
        loaded = true
        _state.update { it.copy(step = SetupStep.MESSENGER, error = null) }
    }

    fun openTokenEditor() {
        _state.update { it.copy(step = SetupStep.TOKEN, error = null, missingToken = false) }
    }

    fun refreshChats() {
        val current = _state.value
        val messenger = current.messenger
        val token = current.token.trim()
        if (messenger == null) {
            _state.update { it.copy(error = "Выберите, куда отправлять") }
            return
        }
        if (token.isBlank()) {
            _state.update {
                it.copy(
                    missingToken = true,
                    error = "Вставьте ключ",
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, missingToken = false) }
            runCatching { loadChats(messenger, token) }
                .onSuccess { (name, chats) ->
                    _state.update {
                        it.copy(
                            loading = false,
                            accountName = name,
                            chats = chats,
                            selectedChat = it.selectedChat?.takeIf { chat -> chats.any { c -> c.id == chat.id } }
                                ?: chats.firstOrNull { chat -> chat.id == it.manualId }
                                ?: it.selectedChat,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.message) }
                }
        }
    }

    fun verifyManualChat() {
        val current = _state.value
        val chatId = current.manualId.trim()
        val messenger = current.messenger
        val token = current.token.trim()
        if (chatId.isBlank() || messenger == null) {
            _state.update { it.copy(error = "Укажите чат") }
            return
        }
        if (token.isBlank()) {
            _state.update { it.copy(missingToken = true, error = "Вставьте ключ") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                when (messenger) {
                    Messenger.TELEGRAM -> telegramClient.resolveChat(token, chatId)
                    Messenger.VK -> vkClient.resolvePeer(token, chatId)
                }
            }.onSuccess { chat ->
                _state.update { it.copy(loading = false, selectedChat = chat, manualId = chat.id) }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.message) }
            }
        }
    }

    private fun checkTokenAndOpenChats() {
        val current = _state.value
        val messenger = current.messenger
        val token = current.token.trim()
        if (messenger == null) {
            _state.update { it.copy(error = "Выберите, куда отправлять") }
            return
        }
        if (token.isBlank()) {
            _state.update { it.copy(missingToken = true, error = "Вставьте ключ") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, missingToken = false) }
            runCatching { loadChats(messenger, token) }
                .onSuccess { (name, chats) ->
                    _state.update {
                        it.copy(
                            loading = false,
                            step = SetupStep.CHAT,
                            accountName = name,
                            chats = chats,
                            selectedChat = it.selectedChat?.takeIf { chat -> chats.any { c -> c.id == chat.id } }
                                ?: chats.firstOrNull { chat -> chat.id == it.manualId }
                                ?: it.selectedChat,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.message) }
                }
        }
    }

    private suspend fun loadChats(messenger: Messenger, token: String): Pair<String, List<ChatCandidate>> {
        return when (messenger) {
            Messenger.TELEGRAM -> telegramClient.getBotName(token) to telegramClient.listRecentChats(token)
            Messenger.VK -> {
                val name = runCatching { vkClient.verifyGroup(token) }.getOrDefault("Группа VK")
                name to vkClient.listConversations(token)
            }
        }
    }

    fun save() {
        val current = _state.value
        val messenger = current.messenger
        val token = current.token.trim()
        val chat = current.selectedChat
            ?: current.manualId.trim().takeIf { it.isNotBlank() }?.let {
                ChatCandidate(id = it, title = it)
            }
        if (messenger == null || chat == null) {
            _state.update { it.copy(error = "Укажите чат") }
            return
        }
        if (token.isBlank()) {
            _state.update { it.copy(missingToken = true, error = "Вставьте ключ") }
            return
        }
        viewModelScope.launch {
            val previous = settingsRepository.current()
            settingsRepository.save(
                previous.copy(
                    messenger = messenger,
                    telegramToken = if (messenger == Messenger.TELEGRAM) token else previous.telegramToken,
                    vkGroupToken = if (messenger == Messenger.VK) token else previous.vkGroupToken,
                    chatId = chat.id,
                    chatTitle = chat.title,
                    configured = true,
                ),
            )
            storedSettings = settingsRepository.current()
            _finished.emit(Unit)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SetupViewModel(
                        settingsRepository = container.settingsRepository,
                        telegramClient = container.telegramClient,
                        vkClient = container.vkClient,
                    ) as T
                }
            }
        }
    }
}
