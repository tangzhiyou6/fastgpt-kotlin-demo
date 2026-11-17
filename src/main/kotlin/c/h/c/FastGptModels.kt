package c.h.c

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * FastGPT 对话消息。
 */
@Serializable
data class ChatMessage(
    @SerialName("role") val role: String = "",
    @SerialName("content") val content: String = ""
)

/**
 * FastGPT variables：本次融合场景只用到 file（模型可下载的文件地址列表）。
 */
@Serializable
data class ChatVariables(
    @SerialName("file") val file: List<String> = emptyList()
)

/**
 * 请求体。
 *
 * 字段名、字段顺序与线上日志 `请求大模型参数` 打印的 JSON 完全一致，便于逐字段对照：
 * messages / responseChatItemId / appId / chatId / detail / stream / retainDatasetCite /
 * variables / system / cTime / business_code
 */
@Serializable
data class FastGptChatRequest(
    @SerialName("messages") val messages: List<ChatMessage> = emptyList(),
    @SerialName("responseChatItemId") val responseChatItemId: String = "",
    @SerialName("appId") val appId: String = "",
    @SerialName("chatId") val chatId: String = "",
    @SerialName("detail") val detail: Boolean = false,
    @SerialName("stream") val stream: Boolean = false,
    @SerialName("retainDatasetCite") val retainDatasetCite: Boolean = false,
    @SerialName("variables") val variables: ChatVariables = ChatVariables(),
    @SerialName("system") val system: String = "0",
    @SerialName("cTime") val cTime: String = "",
    @SerialName("business_code") val businessCode: String = "0"
)

/**
 * FastGPT 非流式响应中的一条候选答案。
 */
@Serializable
data class ChatChoice(
    @SerialName("message") val message: ChatMessage = ChatMessage()
)

/**
 * FastGPT 非流式响应体（stream=false）。
 */
@Serializable
data class FastGptChatResponse(
    @SerialName("choices") val choices: List<ChatChoice> = emptyList()
)

/**
 * 非结构化提取结果：模型自由文本里抽出来的业务字段。
 *
 * 当前融合场景只有一个 scene 字段，序列化后即 `{"scene":""}`。
 */
@Serializable
data class FusionResult(
    @SerialName("scene") val scene: String = ""
)

/**
 * 全局 JSON 配置：
 * - encodeDefaults = true：保证 messages=[]、detail=false 这类默认值也会被打印出来；
 * - explicitNulls = false：不输出 null 字段。
 */
object JsonSupport {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}
