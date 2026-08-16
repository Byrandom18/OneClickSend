package com.oneclicksend.app.network

import com.oneclicksend.app.data.ChatCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

class TelegramClient(private val http: OkHttpClient) {

    suspend fun getBotName(token: String): String {
        val json = call(token, "getMe")
        val result = json.getJSONObject("result")
        val username = result.optString("username").orEmpty()
        val firstName = result.optString("first_name").orEmpty()
        return if (username.isNotBlank()) "@$username" else firstName.ifBlank { "Telegram-бот" }
    }

    suspend fun listRecentChats(token: String): List<ChatCandidate> {
        val json = call(token, "getUpdates", mapOf("limit" to "100", "timeout" to "0"))
        val result = json.optJSONArray("result") ?: JSONArray()
        val chats = linkedMapOf<String, ChatCandidate>()
        for (i in 0 until result.length()) {
            val update = result.getJSONObject(i)
            val message = update.optJSONObject("message")
                ?: update.optJSONObject("edited_message")
                ?: update.optJSONObject("channel_post")
                ?: update.optJSONObject("my_chat_member")?.optJSONObject("chat")?.let { chat ->
                    JSONObject().put("chat", chat)
                }
            val chat = message?.optJSONObject("chat") ?: continue
            val candidate = chat.toCandidate()
            chats[candidate.id] = candidate
        }
        return chats.values.toList()
    }

    suspend fun resolveChat(token: String, chatId: String): ChatCandidate {
        val json = call(token, "getChat", mapOf("chat_id" to chatId.trim()))
        return json.getJSONObject("result").toCandidate()
    }

    suspend fun sendPhoto(token: String, chatId: String, photo: File) = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot${token.trim()}/sendPhoto"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId.trim())
            .addFormDataPart(
                "photo",
                photo.name,
                photo.asRequestBody("image/jpeg".toMediaType()),
            )
            .build()
        val request = Request.Builder().url(url).post(body).build()
        try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ApiException(errorMessage(raw, "Telegram ответил ${response.code}"))
                }
                val json = parseObject(raw)
                if (!json.optBoolean("ok", false)) {
                    throw ApiException(json.optString("description").ifBlank { "Telegram отклонил фото" })
                }
            }
        } catch (error: IOException) {
            throw ApiException("Нет сети или сервер не ответил")
        }
    }

    private suspend fun call(
        token: String,
        method: String,
        params: Map<String, String> = emptyMap(),
    ): JSONObject = withContext(Dispatchers.IO) {
        val builder = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("api.telegram.org")
            .addPathSegment("bot${token.trim()}")
            .addPathSegment(method)
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val request = Request.Builder().url(builder.build()).get().build()
        try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = parseObject(raw)
                if (!response.isSuccessful || !json.optBoolean("ok", false)) {
                    throw ApiException(json.optString("description").ifBlank { "Ошибка Telegram ($method)" })
                }
                json
            }
        } catch (error: IOException) {
            throw ApiException("Нет сети или сервер не ответил")
        }
    }

    private fun JSONObject.toCandidate(): ChatCandidate {
        val id = opt("id")?.toString().orEmpty()
        val title = sequenceOf(
            optString("title"),
            listOf(optString("first_name"), optString("last_name"))
                .filter { it.isNotBlank() }
                .joinToString(" "),
            optString("username").takeIf { it.isNotBlank() }?.let { "@$it" },
        ).firstOrNull { !it.isNullOrBlank() } ?: id
        val type = when (optString("type")) {
            "private" -> "Личные сообщения"
            "group", "supergroup" -> "Группа"
            "channel" -> "Канал"
            else -> optString("type")
        }
        return ChatCandidate(id = id, title = title, subtitle = type)
    }

    private fun parseObject(raw: String): JSONObject {
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            throw ApiException("Непонятный ответ Telegram")
        }
    }

    private fun errorMessage(raw: String, fallback: String): String {
        return try {
            JSONObject(raw).optString("description").ifBlank { fallback }
        } catch (_: Exception) {
            fallback
        }
    }
}
