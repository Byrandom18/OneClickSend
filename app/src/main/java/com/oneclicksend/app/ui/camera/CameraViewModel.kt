package com.oneclicksend.app.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.oneclicksend.app.AppContainer
import com.oneclicksend.app.send.PhotoSendQueue
import com.oneclicksend.app.send.SendUiState
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class CameraViewModel(
    private val sendQueue: PhotoSendQueue,
) : ViewModel() {
    val sendState: StateFlow<SendUiState> = sendQueue.state

    fun onPhotoCaptured(file: File) {
        sendQueue.enqueue(file)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CameraViewModel(container.sendQueue) as T
                }
            }
        }
    }
}
