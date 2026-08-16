package com.oneclicksend.app

import com.oneclicksend.app.data.SettingsRepository
import com.oneclicksend.app.network.TelegramClient
import com.oneclicksend.app.network.VkClient
import com.oneclicksend.app.send.PhotoSendQueue
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(app: OneClickSendApp) {
    val settingsRepository = SettingsRepository(app)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val telegramClient = TelegramClient(httpClient)
    val vkClient = VkClient(httpClient)
    val sendQueue = PhotoSendQueue(
        settingsRepository = settingsRepository,
        telegramClient = telegramClient,
        vkClient = vkClient,
    )
}
