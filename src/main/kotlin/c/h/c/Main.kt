package c.h.c

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("c.h.c.Main")

/** 请求体里的 cTime 字段格式，例如：2026-11-17 16:36:28 Tuesday */
private val CTIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE", Locale.ENGLISH)

/**
 * 单个任务的执行结果：成功带 result，失败带 error。
 */
private data class TaskOutcome(val index: Int, val result: String?, val error: String?)

/**
 * Demo 入口（Ktor + 协程）：按 FASTGPT_CONCURRENCY 的并发度，
 * 向 FastGPT 发起非流式请求并做非结构化提取。
 *
 * 线程模型与线上一致：协程调度在 global-fusion-N 线程池上，
 * 「请求大模型参数」与「非结构化提取结果」两条日志都出自 chat 方法。
 */
fun main() {
    val config = AppConfig.fromEnv()

    log.info("=== FastGPT Kotlin 请求测试 Demo 启动 ===")
    log.info(
        "baseUrl={}, chatId={}, appId={}, mock={}, concurrency={}, file={}",
        config.baseUrl, config.chatId, config.appId, config.mock, config.concurrency, config.fileUrl
    )
    if (config.mock) {
        log.warn("当前为 MOCK 模式（未配置 FASTGPT_API_KEY 或 FASTGPT_MOCK=true），不会真正调用 FastGPT")
    }

    // 协程全部跑完后再汇总，保证日志顺序稳定
    val outcomes = runBlocking { runTasks(config) }

    var failed = 0
    outcomes.forEach { outcome ->
        if (outcome.error != null) {
            failed++
            log.error("任务[{}] 调用失败：{}", outcome.index, outcome.error)
        } else {
            log.info("任务[{}] 融合结果：{}", outcome.index, outcome.result)
        }
    }
    log.info("=== 全部任务结束：成功 {} 个，失败 {} 个 ===", outcomes.size - failed, failed)
}

/**
 * 并发执行 FASTGPT_CONCURRENCY 个任务，全部完成后返回结果。
 */
private suspend fun runTasks(config: AppConfig): List<TaskOutcome> {
    val dispatcher = FusionExecutors.newFusionDispatcher(config.concurrency)
    val client = FastGptClient(
        baseUrl = config.baseUrl,
        apiKey = config.apiKey,
        timeout = Duration.ofSeconds(config.timeoutSeconds),
        mock = config.mock,
        mockDelayMs = config.mockDelayMs
    )

    val outcomes = ConcurrentLinkedQueue<TaskOutcome>()
    try {
        coroutineScope {
            repeat(config.concurrency) { index ->
                launch(dispatcher) {
                    val outcome = try {
                        val result = client.chat(buildRequest(config))
                        TaskOutcome(index, JsonSupport.json.encodeToString(result), null)
                    } catch (t: Throwable) {
                        // 取最内层根因，避免整屏 Java 堆栈淹没真正原因
                        val root = generateSequence(t as Throwable?) { it.cause }.last()
                        TaskOutcome(index, null, "${root::class.simpleName} - ${root.message}")
                    }
                    outcomes.add(outcome)
                }
            }
        }
    } finally {
        dispatcher.close()
        client.close()
    }
    return outcomes.toList()
}

/**
 * 按线上日志的字段顺序组装请求体。
 */
private fun buildRequest(config: AppConfig): FastGptChatRequest {
    return FastGptChatRequest(
        messages = emptyList(),
        responseChatItemId = UUID.randomUUID().toString(),
        appId = config.appId,
        chatId = config.chatId,
        detail = false,
        stream = false,
        retainDatasetCite = false,
        variables = ChatVariables(file = listOf(config.fileUrl)),
        system = config.system,
        cTime = LocalDateTime.now().format(CTIME_FORMATTER),
        businessCode = config.businessCode
    )
}
