package c.h.c

/**
 * Demo 运行配置，全部来自环境变量，方便在 Docker 里注入。
 *
 * | 环境变量                 | 默认值                                              | 说明                          |
 * |------------------------|-----------------------------------------------------|-----------------------------|
 * | FASTGPT_BASE_URL       | https://api.fastgpt.io                              | FastGPT 站点地址              |
 * | FASTGPT_API_KEY        | 空                                                  | FastGPT API Key              |
 * | FASTGPT_CHAT_ID        | 14736041-f59e-4329-9284-f20475ab7bd3              | 应用（App）里的 Chat ID       |
 * | FASTGPT_APP_ID         | 6a85961e6b9786fd6003756a                            | FastGPT 应用 ID              |
 * | FASTGPT_FILE_URL       | 见下方默认附件地址                                    | variables.file 里的文件地址   |
 * | FASTGPT_SYSTEM         | 0                                                   | 请求体 system 字段           |
 * | FASTGPT_BUSINESS_CODE  | 0                                                   | 请求体 business_code 字段     |
 * | FASTGPT_CONCURRENCY    | 3                                                   | 并发线程数（global-fusion-N） |
 * | FASTGPT_TIMEOUT_SECONDS| 180                                                 | 单次请求超时                  |
 * | FASTGPT_MOCK           | 未配置 API Key 时为 true                             | MOCK 模式，不真正请求大模型    |
 * | FASTGPT_MOCK_DELAY_MS  | 20000                                               | MOCK 模式下的模拟耗时（毫秒） |
 */
data class AppConfig(
    val baseUrl: String,
    val apiKey: String,
    val chatId: String,
    val appId: String,
    val fileUrl: String,
    val system: String,
    val businessCode: String,
    val concurrency: Int,
    val timeoutSeconds: Long,
    val mock: Boolean,
    val mockDelayMs: Long
) {
    companion object {

        /** 线上日志里出现过的附件地址（OSS 签名 URL，签名有有效期） */
        const val DEFAULT_FILE_URL: String =
            "http://dsjsjxt-kffwq01-10-104-32-30.oss-cn-anshun-dtcloud-d01-a.ops.dtcloud.spic" +
                "/ima-data-fusion/cxtz_gzbg/3f7518551f3b470d93650fd0942e7865/" +
                "%E9%99%84%E4%BB%B62.%20%E5%9B%BD%E7%94%B5%E6%8A%95%E6%A0%B8%E5%8A%9B%E5%88%9B%E8%8A%AF" +
                "%EF%BC%88%E6%97%A0%E9%94%A1%EF%BC%89%E7%A7%91%E6%8A%80%E6%9C%89%E9%99%90%E5%85%AC%E5%8F%B8" +
                "%E8%B4%A2%E5%8A%A1%E5%B0%BD%E8%B0%83%E6%8A%A5%E5%91%8A.pdf?Expires=1794990988" +
                "&OSSAccessKeyId=4aPZ5FG22UV0OVbB" +
                "&Signature=0xzkwUXMXjXpuEkw5d%2FALid5f4E%3D" +
                "&response-content-disposition=attachment%3Bfilename%3D%22%25E9%2599%2584%25E4%25BB%25B62." +
                "%2520%25E5%259B%25BD%25E7%2594%25B5%25E6%258A%2595%25E6%25A0%25B8%25E5%258A%259B%25E5%2588%259B" +
                "%25E8%258A%25AF%25EF%25BC%2588%25E6%2597%25A0%25E9%2594%25A1%25EF%25BC%2589%25E7%25A7%2591" +
                "%25E6%258A%2580%25E6%259C%2589%25E9%2599%2590%25E5%2585%25AC%25E5%258F%25B8%25E8%25B4%25A2" +
                "%25E5%258A%25A1%25E5%25B0%25BD%25E8%25B0%2583%25E6%258A%25A5%25E5%2591%258A.pdf%22%3B" +
                "filename%2A%3DUTF-8%27%27%25E9%2599%2584%25E4%25BB%25B62.%2520%25E5%259B%25BD%25E7%2594%25B5" +
                "%25E6%258A%2595%25E6%25A0%25B8%25E5%258A%259B%25E5%2588%259B%25E8%258A%25AF%25EF%25BC%2588" +
                "%25E6%2597%25A0%25E9%2594%25A1%25EF%25BC%2589%25E7%25A7%2591%25E6%258A%2580%25E6%259C%2589" +
                "%25E9%2599%2590%25E5%2585%25AC%25E5%258F%25B8%25E8%25B4%25A2%25E5%258A%25A1%25E5%25B0%25BD" +
                "%25E8%25B0%2583%25E6%258A%25A5%25E5%2591%258A.pdf"

        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            val apiKey = env["FASTGPT_API_KEY"]?.trim().orEmpty()
            val mockFlag = env["FASTGPT_MOCK"]?.trim()?.lowercase()
            val mock = mockFlag?.toBooleanStrictOrNull() ?: apiKey.isBlank()

            return AppConfig(
                baseUrl = env["FASTGPT_BASE_URL"]?.trim().orEmpty().ifEmpty { "https://api.fastgpt.io" },
                apiKey = apiKey,
                chatId = env["FASTGPT_CHAT_ID"]?.trim().orEmpty()
                    .ifEmpty { "14736041-f59e-4329-9284-f20475ab7bd3" },
                appId = env["FASTGPT_APP_ID"]?.trim().orEmpty()
                    .ifEmpty { "6a85961e6b9786fd6003756a" },
                fileUrl = env["FASTGPT_FILE_URL"]?.trim().orEmpty().ifEmpty { DEFAULT_FILE_URL },
                system = env["FASTGPT_SYSTEM"]?.trim().orEmpty().ifEmpty { "0" },
                businessCode = env["FASTGPT_BUSINESS_CODE"]?.trim().orEmpty().ifEmpty { "0" },
                concurrency = env["FASTGPT_CONCURRENCY"]?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: 3,
                timeoutSeconds = env["FASTGPT_TIMEOUT_SECONDS"]?.trim()?.toLongOrNull() ?: 180L,
                mock = mock,
                mockDelayMs = env["FASTGPT_MOCK_DELAY_MS"]?.trim()?.toLongOrNull() ?: 20_000L
            )
        }
    }
}
