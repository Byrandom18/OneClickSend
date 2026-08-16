package com.oneclicksend.app.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.oneclicksend.app.R
import com.oneclicksend.app.data.AppSettings
import com.oneclicksend.app.data.ChatCandidate
import com.oneclicksend.app.data.Messenger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    settings: AppSettings,
    state: SetupUiState,
    canCancel: Boolean,
    onLoad: (AppSettings) -> Unit,
    onSelectMessenger: (Messenger) -> Unit,
    onTokenChange: (String) -> Unit,
    onManualIdChange: (String) -> Unit,
    onSelectChat: (ChatCandidate) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onChangeKey: () -> Unit,
    onRefreshChats: () -> Unit,
    onVerifyChat: () -> Unit,
) {
    LaunchedEffect(settings.configured, settings.chatId, settings.messenger) {
        onLoad(settings)
    }

    val showBack = state.step != SetupStep.MESSENGER || canCancel
    val title = when (state.step) {
        SetupStep.MESSENGER -> R.string.setup_title
        SetupStep.TOKEN -> if (state.messenger == Messenger.VK) R.string.token_vk_title else R.string.token_telegram_title
        SetupStep.CHAT -> R.string.chat_title
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    if (showBack) {
                        TextButton(onClick = onBack) {
                            Text(
                                stringResource(
                                    if (state.step == SetupStep.MESSENGER) R.string.to_camera else R.string.back_action,
                                ),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        when (state.step) {
            SetupStep.MESSENGER -> MessengerStep(
                padding = innerPadding,
                selected = state.messenger,
                error = state.error,
                onSelect = onSelectMessenger,
                onNext = onNext,
            )
            SetupStep.TOKEN -> TokenStep(
                padding = innerPadding,
                state = state,
                onTokenChange = onTokenChange,
                onNext = onNext,
            )
            SetupStep.CHAT -> ChatStep(
                padding = innerPadding,
                state = state,
                onSelectChat = onSelectChat,
                onManualIdChange = onManualIdChange,
                onRefresh = onRefreshChats,
                onVerify = onVerifyChat,
                onChangeKey = onChangeKey,
                onSave = onNext,
            )
        }
    }
}

@Composable
private fun MessengerStep(
    padding: PaddingValues,
    selected: Messenger?,
    error: String?,
    onSelect: (Messenger) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.setup_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        MessengerCard(
            title = stringResource(R.string.messenger_telegram),
            subtitle = stringResource(R.string.messenger_telegram_hint),
            selected = selected == Messenger.TELEGRAM,
            onClick = { onSelect(Messenger.TELEGRAM) },
        )
        Spacer(Modifier.height(12.dp))
        MessengerCard(
            title = stringResource(R.string.messenger_vk),
            subtitle = stringResource(R.string.messenger_vk_hint),
            selected = selected == Messenger.VK,
            onClick = { onSelect(Messenger.VK) },
        )
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.continue_action))
        }
    }
}

@Composable
private fun MessengerCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TokenStep(
    padding: PaddingValues,
    state: SetupUiState,
    onTokenChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    val isTelegram = state.messenger != Messenger.VK
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(if (isTelegram) R.string.token_telegram_help else R.string.token_vk_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.token,
            onValueChange = onTokenChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            placeholder = {
                Text(stringResource(if (isTelegram) R.string.token_telegram_hint else R.string.token_vk_hint))
            },
        )
        if (!state.error.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(state.error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onNext,
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.check_token))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ChatStep(
    padding: PaddingValues,
    state: SetupUiState,
    onSelectChat: (ChatCandidate) -> Unit,
    onManualIdChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onVerify: () -> Unit,
    onChangeKey: () -> Unit,
    onSave: () -> Unit,
) {
    val isTelegram = state.messenger == Messenger.TELEGRAM
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp),
    ) {
        if (state.accountName.isNotBlank()) {
            Text(state.accountName, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.loading && state.chats.isEmpty()) {
                item { CircularProgressIndicator() }
            } else if (state.chats.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            if (isTelegram) R.string.no_chats_telegram else R.string.no_chats_vk,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.chats, key = { it.id }) { chat ->
                    ChatRow(
                        chat = chat,
                        selected = state.selectedChat?.id == chat.id,
                        onClick = { onSelectChat(chat) },
                    )
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onRefresh, enabled = !state.loading) {
                    Text(stringResource(R.string.refresh_chats))
                }
                TextButton(onClick = onChangeKey, enabled = !state.loading) {
                    Text(stringResource(R.string.change_key))
                }
                OutlinedTextField(
                    value = state.manualId,
                    onValueChange = onManualIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(
                            stringResource(
                                if (isTelegram) R.string.chat_manual_telegram else R.string.chat_manual_vk,
                            ),
                        )
                    },
                    placeholder = { Text(stringResource(R.string.chat_id_hint)) },
                )
                TextButton(onClick = onVerify, enabled = !state.loading) {
                    Text(stringResource(R.string.verify_chat))
                }
            }
        }
        if (!state.error.isNullOrBlank()) {
            Text(state.error, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = onSave,
            enabled = !state.loading && !state.missingToken,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.save_chat))
        }
    }
}

@Composable
private fun ChatRow(
    chat: ChatCandidate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(chat.title, style = MaterialTheme.typography.titleMedium)
            if (chat.subtitle.isNotBlank()) {
                Text(chat.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
