package c.h.c

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.time.Duration
import kotlinx.coroutines.delay
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * FastGPT 大模型客户端（Ktor Client + 协程）。
 *
 * <p>本类复现线上 `global-fusion` 融合服务的调用链路：
 * 1. 按线上日志的字段顺序组装请求体，以非流式（stream=false）方式调用 FastGPT；
 * 2. 对模型返回的文本做「非结构化提取」。
 *
 * <p>日志与线上逐行对应：logger = c.h.c.FastGptClient，方法 = chat，第 81 / 107 行为两条关键日志。
 */
class FastGptClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val timeout: Duration = Duration.ofSeconds(180),
    private val mock: Boolean = false,
    private val mockDelayMs: Long = 20_000L
) {

    private val log: Logger = LoggerFactory.getLogger(FastGptClient::class.java)

    /** Ktor CIO 引擎：不依赖 Netty，镜像体积更小，足够打大模型 HTTP 请求 */
    private val httpClient: HttpClient = HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(JsonSupport.json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = timeout.toMillis()
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = timeout.toMillis()
        }
    }

    /** 释放 Ktor 客户端持有的连接池 */
    fun close() {
        httpClient.close()
    }

    /** MOCK 模式：协程 delay 模拟一次耗时相同的响应，不占用任何线程 */
    private suspend fun mockExchange(): String {
        if (mockDelayMs > 0) {
            delay(mockDelayMs)
        }
        return MOCK_RESPONSE
    }

    /** 从响应体取 choices[0].message.content；解析失败时原样返回，便于兜底 */
    private fun extractContent(raw: String): String {
        return try {
            val response = JsonSupport.json.decodeFromString(FastGptChatResponse.serializer(), raw)
            response.choices.firstOrNull()?.message?.content ?: raw
        } catch (e: Exception) {
            raw
        }
    }

    /**
     * 发起一次 FastGPT 对话请求（非流式），并返回非结构化提取结果。
     * 方法体内同步完成「请求 -> 提取 -> 打日志」，使第 81 / 107 行两条日志与线上逐行对齐。
     */
    suspend fun chat(request: FastGptChatRequest): FusionResult {
        val body = JsonSupport.json.encodeToString(request)
        log.info("请求大模型参数：{}", body)

        // ------------------------------------------------------------------
        // 发起请求。真实模式走 FastGPT 的 OpenAI 兼容接口 /v1/chat/completions，
        // 非流式（stream=false）；MOCK 模式用协程 delay 模拟一次相同耗时的响应。
        //
        // 这里是挂起函数，HTTP 等待期间不占用线程，global-fusion 线程可以同时
        // 处理其它任务；日志线程名依然是 global-fusion-N。
        // ------------------------------------------------------------------
        val raw = exchange(body)

        // ------------------------------------------------------------------
        // 非结构化提取（不依赖模型的结构化输出能力 response_format/json_schema）：
        //   1. 优先从 ```json 代码块或裸 JSON 对象里取 scene；
        //   2. JSON 解析失败时用正则 "\"scene\"\s*:\s*\"([^\"]*)\"" 兜底；
        //   3. 都取不到时返回空值，即 {"scene":""}（与线上日志一致）。
        //   4. 抽取逻辑与模型版本解耦：模型换版不影响兜底行为。
        //
        // 这里刻意不要求模型输出严格 JSON：线上大模型返回的是带解释的自然语言，
        // 抽取规则要能容忍 ```json 包裹、前后夹带说明文字等情况。
        //
        // 提取阶段与请求阶段解耦，方便线上分别定位「网络耗时」和「抽取耗时」。
        // 若线上出现提取为空，先回看第 81 行的 variables.file 是否生效。
        // ------------------------------------------------------------------
        val content = extractContent(raw)
        val result = Extractor.extract(content)
        log.info("非结构化提取结果：{}", JsonSupport.json.encodeToString(result))
        return result
    }

    /**
     * 真实模式调用 FastGPT，MOCK 模式走本地模拟。
     *
     * 非 2xx 响应会抛出带状态码和响应体的异常，方便测试时直接定位是 Key 不对、
     * chatId 不存在，还是附件地址失效。
     */
    private suspend fun exchange(body: String): String {
        if (mock) {
            return mockExchange()
        }
        val response = httpClient.post("${baseUrl.trimEnd('/')}/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            bearerAuth(apiKey)
            // 用 TextContent 直接发送原始 JSON，避免被 ContentNegotiation 再序列化一次
            setBody(TextContent(body, ContentType.Application.Json))
        }
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw IllegalStateException(
                "FastGPT 返回异常状态 HTTP ${response.status.value}：${text.take(500)}"
            )
        }
        return text
    }

    companion object {

        /**
         * MOCK 模式的响应体：模拟大模型返回「自由文本 + 空 scene」。
         */
        private const val MOCK_RESPONSE: String = """
            {"choices":[{"message":{"role":"assistant","content":"已读取上传的《附件2. 国电投核力创苇（无锡）科技有限公司财务岗调研报告.pdf》并完成初步解析，未能识别出明确的业务场景字段。\n```json\n{\"scene\":\"\"}\n```"}}]}
        """
    }
}
