package com.oneclicksend.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oneclicksend.app.data.AppSettings
import com.oneclicksend.app.data.SettingsRepository
import com.oneclicksend.app.ui.camera.CameraScreen
import com.oneclicksend.app.ui.camera.CameraViewModel
import com.oneclicksend.app.ui.setup.SetupScreen
import com.oneclicksend.app.ui.setup.SetupStep
import com.oneclicksend.app.ui.setup.SetupViewModel

@Composable
fun AppRoot(
    settingsRepository: SettingsRepository,
    setupViewModel: SetupViewModel,
    cameraViewModel: CameraViewModel,
) {
    var bootstrapped by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(AppSettings()) }
    val setupState by setupViewModel.state.collectAsStateWithLifecycle()
    val sendState by cameraViewModel.sendState.collectAsStateWithLifecycle()
    var editingChat by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings = settingsRepository.current()
        bootstrapped = true
        settingsRepository.settings.collect { settings = it }
    }

    LaunchedEffect(setupViewModel) {
        setupViewModel.finished.collect {
            editingChat = false
        }
    }

    if (!bootstrapped) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0F14)))
        return
    }

    if (!settings.configured || editingChat) {
        SetupScreen(
            settings = settings,
            state = setupState,
            canCancel = settings.configured && setupState.step == SetupStep.MESSENGER,
            onLoad = setupViewModel::loadFrom,
            onSelectMessenger = setupViewModel::selectMessenger,
            onTokenChange = setupViewModel::onTokenChange,
            onManualIdChange = setupViewModel::onManualIdChange,
            onSelectChat = setupViewModel::selectChat,
            onNext = setupViewModel::goNext,
            onBack = {
                if (setupState.step != SetupStep.MESSENGER) {
                    setupViewModel.goBack()
                } else if (settings.configured) {
                    editingChat = false
                }
            },
            onChangeKey = setupViewModel::openTokenEditor,
            onRefreshChats = setupViewModel::refreshChats,
            onVerifyChat = setupViewModel::verifyManualChat,
        )
    } else {
        CameraScreen(
            settings = settings,
            sendState = sendState,
            onChangeChat = {
                editingChat = true
                setupViewModel.openChatPicker()
            },
            onPhotoCaptured = cameraViewModel::onPhotoCaptured,
        )
    }
}
