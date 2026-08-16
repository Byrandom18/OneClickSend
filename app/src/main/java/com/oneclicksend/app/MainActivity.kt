package com.oneclicksend.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oneclicksend.app.ui.AppRoot
import com.oneclicksend.app.ui.camera.CameraViewModel
import com.oneclicksend.app.ui.setup.SetupViewModel
import com.oneclicksend.app.ui.theme.OneClickSendTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as OneClickSendApp
        setContent {
            OneClickSendTheme {
                val setupViewModel: SetupViewModel = viewModel(
                    factory = remember { SetupViewModel.factory(app.container) },
                )
                val cameraViewModel: CameraViewModel = viewModel(
                    factory = remember { CameraViewModel.factory(app.container) },
                )
                AppRoot(
                    settingsRepository = app.container.settingsRepository,
                    setupViewModel = setupViewModel,
                    cameraViewModel = cameraViewModel,
                )
            }
        }
    }
}
