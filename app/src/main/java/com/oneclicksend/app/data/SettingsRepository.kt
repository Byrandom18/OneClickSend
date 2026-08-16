package com.oneclicksend.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "one_click_send_settings",
)

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = dataStore.data.first().toSettings()

    suspend fun save(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.MESSENGER] = settings.messenger?.name.orEmpty()
            prefs[Keys.TELEGRAM_TOKEN] = settings.telegramToken
            prefs[Keys.VK_GROUP_TOKEN] = settings.vkGroupToken
            prefs[Keys.CHAT_ID] = settings.chatId
            prefs[Keys.CHAT_TITLE] = settings.chatTitle
            prefs[Keys.CONFIGURED] = settings.configured
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val messengerName = this[Keys.MESSENGER].orEmpty()
        return AppSettings(
            messenger = messengerName.takeIf { it.isNotBlank() }?.let { Messenger.valueOf(it) }
                ?: if (this[Keys.CONFIGURED] == true) Messenger.TELEGRAM else null,
            telegramToken = this[Keys.TELEGRAM_TOKEN].orEmpty(),
            vkGroupToken = this[Keys.VK_GROUP_TOKEN].orEmpty(),
            chatId = this[Keys.CHAT_ID].orEmpty(),
            chatTitle = this[Keys.CHAT_TITLE].orEmpty(),
            configured = this[Keys.CONFIGURED] ?: false,
        )
    }

    private object Keys {
        val MESSENGER = stringPreferencesKey("messenger")
        val TELEGRAM_TOKEN = stringPreferencesKey("telegram_token")
        val VK_GROUP_TOKEN = stringPreferencesKey("vk_group_token")
        val CHAT_ID = stringPreferencesKey("chat_id")
        val CHAT_TITLE = stringPreferencesKey("chat_title")
        val CONFIGURED = booleanPreferencesKey("configured")
    }
}
