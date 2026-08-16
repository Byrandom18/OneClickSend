package com.oneclicksend.app.network

import com.oneclicksend.app.data.ChatCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.random.Random

class VkClient(private val http: OkHttpClient) {

    suspend fun verifyGroup(token: String): String {
        val json = method(token, "groups.getById")
        val group = json.optJSONArray("response")?.optJSONObject(0)
            ?: json.optJSONObject("response")?.optJSONArray("groups")?.optJSONObject(0)
        return group?.optString("name")?.ifBlank { "Группа VK" } ?: "Группа VK"
    }

    suspend fun listConversations(token: String): List<ChatCandidate> {
        val json = method(
            token,
            "messages.getConversations",
            mapOf("count" to "40", "extended" to "1", "filter" to "all"),
        )
        val response = json.optJSONObject("response") ?: return emptyList()
        val items = response.optJSONArray("items") ?: return emptyList()
        val profiles = response.optJSONArray("profiles")
        val groups = response.optJSONArray("groups")
        val names = mutableMapOf<Long, String>()
        if (profiles != null) {
            for (i in 0 until profiles.length()) {
                val profile = profiles.getJSONObject(i)
                names[profile.getLong("id")] = listOf(
                    profile.optString("first_name"),
                    profile.optString("last_name"),
                ).filter { it.isNotBlank() }.joinToString(" ")
            }
        }
        if (groups != null) {
            for (i in 0 until groups.length()) {
                val group = groups.getJSONObject(i)
                names[-group.getLong("id")] = group.optString("name")
            }
        }
        val chats = mutableListOf<ChatCandidate>()
        for (i in 0 until items.length()) {
            val conversation = items.getJSONObject(i).optJSONObject("conversation") ?: continue
            val peer = conversation.optJSONObject("peer") ?: continue
            val peerId = peer.optLong("id")
            val type = peer.optString("type")
            val title = conversation.optJSONObject("chat_settings")?.optString("title")
                ?: names[peerId]
                ?: peerId.toString()
            val subtitle = when (type) {
                "user" -> "Сообщения сообщества"
                "chat" -> "Беседа"
                "group", "page" -> "Сообщество"
                else -> type
            }
            chats += ChatCandidate(id = peerId.toString(), title = title, subtitle = subtitle)
        }
        return chats
    }

    suspend fun resolvePeer(token: String, peerId: String): ChatCandidate {
        val id = peerId.trim()
        val conversations = runCatching { listConversations(token) }.getOrDefault(emptyList())
        conversations.firstOrNull { it.id == id }?.let { return it }
        return ChatCandidate(id = id, title = "VK $id", subtitle = "peer_id")
    }

    suspend fun sendPhoto(token: String, peerId: String, photo: File) = withContext(Dispatchers.IO) {
        val uploadServer = method(
            token,
            "photos.getMessagesUploadServer",
            mapOf("peer_id" to peerId.trim()),
        ).getJSONObject("response")
        val uploadUrl = uploadServer.getString("upload_url")
        val uploaded = uploadToServer(uploadUrl, photo)
        val saved = saveUploadedPhoto(token, uploaded)
        val photoJson = saved.optJSONArray("response")?.optJSONObject(0)
            ?: throw ApiException("VK не сохранил фото")
        val ownerId = photoJson.getLong("owner_id")
        val photoId = photoJson.getLong("id")
        val accessKey = photoJson.optString("access_key")
        val attachment = buildString {
            append("photo").append(ownerId).append("_").append(photoId)
            if (accessKey.isNotBlank()) append("_").append(accessKey)
        }
        method(
            token,
            "messages.send",
            mapOf(
                "peer_id" to peerId.trim(),
                "random_id" to Random.nextInt().toString(),
                "attachment" to attachment,
            ),
        )
    }

    private suspend fun uploadToServer(uploadUrl: String, photo: File): JSONObject {
        val preferredField = if (isBulkUpload(uploadUrl)) "file1" else "photo"
        val first = uploadPhoto(uploadUrl, photo, preferredField)
        if (saveParamsFromUpload(first) != null) return first
        val fallbackField = if (preferredField == "file1") "photo" else "file1"
        val second = uploadPhoto(uploadUrl, photo, fallbackField)
        if (saveParamsFromUpload(second) != null) return second
        return second
    }

    private fun isBulkUpload(uploadUrl: String): Boolean {
        return uploadUrl.contains("bulk_upload", ignoreCase = true) ||
            uploadUrl.contains("/v2/", ignoreCase = true)
    }

    private suspend fun saveUploadedPhoto(token: String, uploaded: JSONObject): JSONObject {
        val variants = saveParamVariants(uploaded)
        if (variants.isEmpty()) {
            throw ApiException("VK не принял файл при загрузке. Попробуйте ещё раз.")
        }
        var lastError: Exception? = null
        for (params in variants) {
            try {
                return method(token, "photos.saveMessagesPhoto", params)
            } catch (error: ApiException) {
                lastError = error
            }
        }
        throw lastError ?: ApiException("VK не сохранил фото")
    }

    private fun saveParamVariants(uploaded: JSONObject): List<Map<String, String>> {
        val server = uploaded.opt("server")?.toString().orEmpty()
        val hash = uploaded.opt("hash")?.toString().orEmpty()
        if (server.isBlank() || server == "null") return emptyList()
        val photos = buildList {
            val extracted = extractPhotoParam(uploaded)
            if (extracted.isNotBlank()) add(extracted)
            uploaded.optJSONObject("files")?.toString()?.takeIf { it.isNotBlank() && it != "{}" }?.let { add(it) }
        }.distinct()
        return photos.map { photo ->
            buildMap {
                put("photo", photo)
                put("server", server)
                if (hash.isNotBlank() && hash != "null") put("hash", hash)
            }
        }
    }

    private fun saveParamsFromUpload(uploaded: JSONObject): Map<String, String>? {
        return saveParamVariants(uploaded).firstOrNull()
    }

    private fun extractPhotoParam(uploaded: JSONObject): String {
        val files = uploaded.optJSONObject("files")
        if (files != null) {
            val keys = files.keys()
            while (keys.hasNext()) {
                val file = files.optJSONObject(keys.next()) ?: continue
                val sha = file.optString("sha")
                val secret = file.optString("secret")
                if (sha.isNotBlank() && secret.isNotBlank()) {
                    return "${sha}_${secret}"
                }
            }
        }
        val rawPhoto = uploaded.opt("photo") ?: return ""
        if (rawPhoto == JSONObject.NULL) return ""
        val photoString = when (rawPhoto) {
            is JSONArray -> rawPhoto.toString()
            is JSONObject -> rawPhoto.toString()
            else -> rawPhoto.toString()
        }
        return if (photoString.isBlank() || photoString == "[]" || photoString == "null") "" else photoString
    }

    private suspend fun uploadPhoto(
        uploadUrl: String,
        photo: File,
        fieldName: String,
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                fieldName,
                "photo.jpg",
                photo.asRequestBody("image/jpeg".toMediaType()),
            )
            .build()
        val request = Request.Builder().url(uploadUrl).post(body).build()
        try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ApiException("VK не принял загрузку фото (${response.code})")
                }
                try {
                    JSONObject(raw)
                } catch (_: Exception) {
                    throw ApiException("Непонятный ответ загрузки VK")
                }
            }
        } catch (_: IOException) {
            throw ApiException("Нет сети или сервер не ответил")
        }
    }

    private suspend fun method(
        token: String,
        name: String,
        params: Map<String, String> = emptyMap(),
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.vk.com")
            .addPathSegment("method")
            .addPathSegment(name)
            .build()
        val form = FormBody.Builder()
            .add("access_token", token.trim())
            .add("v", API_VERSION)
        params.forEach { (key, value) -> form.add(key, value) }
        val request = Request.Builder().url(url).post(form.build()).build()
        try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = try {
                    JSONObject(raw)
                } catch (_: Exception) {
                    throw ApiException("Непонятный ответ VK")
                }
                val error = json.optJSONObject("error")
                if (!response.isSuccessful || error != null) {
                    throw ApiException(
                        error?.optString("error_msg")?.ifBlank { null }
                            ?: "Ошибка VK ($name)",
                    )
                }
                json
            }
        } catch (_: IOException) {
            throw ApiException("Нет сети или сервер не ответил")
        }
    }

    private companion object {
        const val API_VERSION = "5.199"
    }
}
