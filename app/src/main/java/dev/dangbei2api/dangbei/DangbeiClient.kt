package dev.dangbei2api.dangbei

import dev.dangbei2api.util.Logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * 当贝 AI 接口客户端（纯 HTTP 直连，无需 WASM 签名）。
 *
 * 实测：只带 token + 基础头即可调通，sign/nonce/timestamp 可省略。
 *
 * 聊天流程：generateId -> batch/create -> chatApi/v2/chat(SSE)
 */
class DangbeiClient {

    companion object {
        const val BASE = "https://ai-api.dangbei.net/ai-search"
        const val APP_TYPE = "7"
        const val APP_VERSION = "1.3.9"
        const val CLIENT_VER = "1.0.2"
    }

    class ApiException(msg: String) : Exception(msg)

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)   // SSE 长连接，但兜底 5 分钟，防止永久挂起
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun baseHeaders(token: String): Map<String, String> = mapOf(
        "token" to token,
        "apptype" to APP_TYPE,
        "appversion" to APP_VERSION,
        "client-ver" to CLIENT_VER,
        "lang" to "zh",
        "content-type" to "application/json"
    )

    /** 取一个新 id（建会话用 / 消息 uuid） */
    fun generateId(token: String): String {
        val req = Request.Builder()
            .url("$BASE/commonApi/v1/generateId")
            .apply { baseHeaders(token).forEach { (k, v) -> addHeader(k, v) } }
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: throw ApiException("generateId 空响应")
            if (!resp.isSuccessful) throw ApiException("generateId HTTP ${resp.code}: ${body.take(200)}")
            val j = JSONObject(body)
            if (!j.optBoolean("success", false)) {
                throw ApiException("generateId 失败: ${j.optString("errMessage")}")
            }
            return j.optString("data")
        }
    }

    /** 新建会话，返回 conversationId */
    fun createConversation(token: String, title: String = "新对话"): String {
        val payload = JSONObject().apply {
            put("conversationList", JSONArray().put(JSONObject().apply {
                put("title", title)
                put("conversationType", 1)
            }))
        }.toString()

        val req = Request.Builder()
            .url("$BASE/conversationApi/v1/batch/create")
            .apply { baseHeaders(token).forEach { (k, v) -> addHeader(k, v) } }
            .post(payload.toRequestBody(jsonType))
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: throw ApiException("createConversation 空响应")
            if (!resp.isSuccessful) throw ApiException("createConversation HTTP ${resp.code}: ${body.take(200)}")
            val j = JSONObject(body)
            if (!j.optBoolean("success", false)) {
                throw ApiException("createConversation 失败: ${j.optString("errMessage")}")
            }
            return j.optJSONObject("data")?.optString("conversationId")
                ?: throw ApiException("createConversation 无 conversationId")
        }
    }

    /**
     * 发起聊天（SSE 流式）。
     * @param conversationId 目标会话
     * @param question       用户问题（完整 prompt）
     * @param modelCode      当贝 model 字段
     * @param search         联网开关
     * @param onDelta        增量回调（每一段回答文本）
     * @return 完整回答文本
     */
    fun chatStream(
        token: String,
        conversationId: String,
        question: String,
        modelCode: String,
        search: Boolean,
        deepThink: Boolean = false,
        onDelta: ((String) -> Unit)? = null
    ): String {
        // uuid 仅作消息标识，generateId 失败时用本地随机值兜底，避免整个聊天失败
        val uuid = try {
            val id = generateId(token)
            if (id.isNullOrEmpty()) java.util.UUID.randomUUID().toString().replace("-", "") else id
        } catch (e: Exception) {
            Logger.log("generateId 失败，使用本地 uuid：${e.message}")
            java.util.UUID.randomUUID().toString().replace("-", "")
        }

        val chatOption = JSONObject().apply {
            put("searchKnowledge", search)
            put("searchAllKnowledge", search)
            put("searchSharedKnowledge", search)
        }

        val payload = JSONObject().apply {
            put("stream", true)
            put("botCode", "AI_SEARCH")
            put("conversationId", conversationId)
            put("question", question)
            put("model", modelCode)
            put("chatOption", chatOption)
            put("knowledgeList", JSONArray())
            put("anonymousKey", "")
            put("uuid", uuid)
            put("chatId", uuid)
            put("files", JSONArray())
            put("reference", JSONArray())
            put("role", "user")
            put("status", "local")
            put("content", question)
            // 深度思考：userAction=deep；普通：online
            put("userAction", if (deepThink) "deep" else "online")
            put("agentId", "")
        }.toString()

        val req = Request.Builder()
            .url("$BASE/chatApi/v2/chat")
            .apply { baseHeaders(token).forEach { (k, v) -> addHeader(k, v) } }
            .post(payload.toRequestBody(jsonType))
            .build()

        val full = StringBuilder()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                throw ApiException("chat HTTP ${resp.code}: ${err.take(300)}")
            }
            val reader = BufferedReader(InputStreamReader(resp.body!!.byteStream(), Charsets.UTF_8))
            val dataBuf = StringBuilder()

            // 解析一条 data 负载：取 type==answer 的 content；不依赖 event 名，避免漏内容
            fun handleData(raw: String) {
                val t = raw.trim()
                if (t.isEmpty() || t == "[DONE]") return
                try {
                    val j = JSONObject(t)
                    val type = j.optString("type")
                    val contentType = j.optString("content_type")
                    val content = j.optString("content")
                    // 只取正式回答：type=answer 且 content_type 非 progress（过滤"联网搜索中…"中间态）
                    if (type == "answer" && contentType != "progress" && content.isNotEmpty()) {
                        full.append(content)
                        onDelta?.invoke(content)
                    }
                } catch (e: Exception) {
                    // 非 JSON（可能是多行 data 的片段），先忽略
                }
            }

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line!!
                when {
                    l.isEmpty() -> {
                        // 空行 = 一个 SSE 事件结束
                        if (dataBuf.isNotEmpty()) {
                            handleData(dataBuf.toString())
                            dataBuf.setLength(0)
                        }
                    }
                    l.startsWith("data:") -> {
                        dataBuf.append(l.removePrefix("data:").trim())
                    }
                    l.startsWith("event:") -> {
                        // event 名不需要，跳过（靠 data 的 type 字段判断）
                    }
                    else -> {
                        // data 的多行续接
                        dataBuf.append(l.trim())
                    }
                }
            }
            // 收尾：最后一条没有 trailing 空行
            if (dataBuf.isNotEmpty()) handleData(dataBuf.toString())
        }

        if (full.isEmpty()) throw ApiException("未收到有效回答（可能 token 失效或模型不支持）")
        return full.toString()
    }
}
