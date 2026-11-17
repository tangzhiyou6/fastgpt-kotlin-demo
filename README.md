# FastGPT Kotlin 请求测试 Demo（Ktor + 协程）

用 Kotlin + **Ktor Client** 复现线上 `global-fusion` 融合服务调用 FastGPT 的链路，并提供可在 **linux/amd64** 上直接运行的 Docker 镜像。

Demo 输出的日志格式、logger 名、方法名、行号、线程名都与线上日志一一对应，可以逐行对照：

```
2026-11-17 16:36:28.848  INFO [global-fusion-3] c.h.c.FastGptClient.chat[81]  - 请求大模型参数：{"messages":[],...}
2026-11-17 16:36:49.223  INFO [global-fusion-1] c.h.c.FastGptClient.chat[107] - 非结构化提取结果：{"scene":""}
```

| 线上日志元素 | Demo 中的实现位置 |
|---|---|
| `c.h.c.FastGptClient.chat[81]` | `src/main/kotlin/c/h/c/FastGptClient.kt` 第 81 行 `log.info("请求大模型参数：{}")` |
| `c.h.c.FastGptClient.chat[107]` | 同文件第 107 行 `log.info("非结构化提取结果：{}")` |
| `[global-fusion-3]` | `FusionExecutors.newFusionDispatcher()` 命名的协程调度器线程 `global-fusion-N` |
| `请求大模型参数：{...}` | `FastGptChatRequest` 序列化结果，字段名与顺序与线上完全一致 |
| `非结构化提取结果：{"scene":""}` | `Extractor` 从模型自由文本中抽取 `scene`，抽不到即为空串 |

demo 实测输出（linux/amd64 容器内）：

```
2026-11-17 17:38:39.558  INFO [global-fusion-3] c.h.c.FastGptClient.chat[81]  - 请求大模型参数：{"messages":[],"responseChatItemId":"c58a36f2-...","appId":"6a85961e6b9786fd6003756a","chatId":"14736041-f59e-4329-9284-f20475ab7bd3","detail":false,"stream":false,"retainDatasetCite":false,"variables":{"file":["http://.../附件2. ...pdf?..."]},"system":"0","cTime":"2026-11-17 17:38:39 Tuesday","business_code":"0"}
2026-11-17 17:38:41.110  INFO [global-fusion-3] c.h.c.FastGptClient.chat[107] - 非结构化提取结果：{"scene":""}
```

---

## 一、技术栈

| 环节 | 选型 | 说明 |
|---|---|---|
| HTTP 客户端 | **Ktor Client 3.3.1**（CIO 引擎） | Kotlin 官方客户端框架，`suspend` 风格，支持超时/重试/内容协商插件 |
| 并发 | **kotlinx-coroutines 1.10.2** | `runBlocking` + `coroutineScope` + `launch`，IO 等待不占线程 |
| 调度器 | 自定义 `ExecutorCoroutineDispatcher` | 线程名固定为 `global-fusion-N`，与线上一致 |
| 序列化 | **kotlinx-serialization-json 1.9.0** | 请求/响应 DTO |
| 日志 | **SLF4J 2.0.16 + Logback 1.5.16** | 日志格式与线上一致 |
| 构建 | **Gradle 8.14.7 + Kotlin 2.2.20** | Gradle Wrapper 随包提供 |
| 运行时 | **JDK 21（eclipse-temurin:21-jre）** | 镜像运行阶段 |

---

## 二、目录结构

```
fastgpt-kotlin-demo/
├── Dockerfile                     # 多阶段构建，运行阶段锁定 linux/amd64
├── docker-compose.yml             # 一条命令起容器
├── build.gradle.kts               # Ktor / 协程 / kotlinx-serialization 依赖
├── settings.gradle.kts
├── gradlew, gradle/               # Gradle Wrapper 8.14.7（无 Gradle 环境也能构建）
└── src/main/
    ├── kotlin/c/h/c/
    │   ├── Main.kt                # 入口：runBlocking + 协程并发调度
    │   ├── FastGptClient.kt       # ★ 日志行号 81 / 107 对应这里（suspend fun chat）
    │   ├── FastGptModels.kt       # 请求/响应 DTO，字段顺序 = 线上日志
    │   ├── Extractor.kt           # 非结构化提取（代码块 → 裸 JSON → 正则兜底）
    │   ├── AppConfig.kt           # 环境变量配置
    │   └── FusionExecutors.kt     # global-fusion-N 协程调度器
    └── resources/logback.xml      # 日志格式与线上一致
```

---

## 三、快速开始（在 linux/amd64 服务器上）

### 方式 A：服务器上直接构建

```bash
# 1. 上传/克隆本目录到服务器
cd fastgpt-kotlin-demo

# 2. 构建 linux/amd64 镜像（服务器本身就是 amd64，直接 build 即可）
docker build -t fastgpt-kotlin-demo:1.0.0 .

# 3. 先用 MOCK 模式验证链路（不花钱、不需要网络）
docker run --rm -e FASTGPT_MOCK=true -e FASTGPT_MOCK_DELAY_MS=1000 fastgpt-kotlin-demo:1.0.0
```

### 方式 B：Mac / arm64 上交叉构建 amd64 镜像

```bash
# buildx 交叉构建，产物就是 linux/amd64
docker buildx build --platform linux/amd64 -t fastgpt-kotlin-demo:1.0.0 --load .

# 导出后拷到 amd64 服务器
docker save fastgpt-kotlin-demo:1.0.0 | gzip > fastgpt-kotlin-demo-1.0.0-amd64.tar.gz
scp fastgpt-kotlin-demo-1.0.0-amd64.tar.gz user@server:/opt/

# 服务器上导入并运行
docker load -i /opt/fastgpt-kotlin-demo-1.0.0-amd64.tar.gz
docker run --rm fastgpt-kotlin-demo:1.0.0
```

### 方式 C：docker compose

```bash
export FASTGPT_API_KEY=your-key     # 需要真实调用时再填
docker compose up --build
```

---

## 四、配置项（环境变量）

| 变量 | 默认值 | 说明 |
|---|---|---|
| `FASTGPT_BASE_URL` | `https://api.fastgpt.io` | FastGPT 站点地址；私有化部署换成自己的域名 |
| `FASTGPT_API_KEY` | 空 | FastGPT API Key；**不填则自动进入 MOCK 模式** |
| `FASTGPT_CHAT_ID` | `14736041-f59e-4329-9284-f20475ab7bd3` | 应用里的 Chat ID |
| `FASTGPT_APP_ID` | `6a85961e6b9786fd6003756a` | FastGPT 应用 ID |
| `FASTGPT_FILE_URL` | 线上日志中的 OSS 附件地址 | `variables.file` 里的文件地址（OSS 签名 URL 有有效期，过期需换新） |
| `FASTGPT_SYSTEM` | `0` | 请求体 `system` 字段 |
| `FASTGPT_BUSINESS_CODE` | `0` | 请求体 `business_code` 字段 |
| `FASTGPT_CONCURRENCY` | `3` | 并发数，即 `global-fusion-N` 的 N |
| `FASTGPT_TIMEOUT_SECONDS` | `180` | 单次请求超时（Ktor `HttpTimeout` 插件） |
| `FASTGPT_MOCK` | 未配置 API Key 时为 `true` | MOCK 模式：本地模拟一次耗时相同的响应 |
| `FASTGPT_MOCK_DELAY_MS` | `20000` | MOCK 模式的模拟耗时（线上两条日志相隔约 20.4 秒） |

真实调用示例：

```bash
docker run --rm \
  -e FASTGPT_API_KEY=fastgpt-xxxxxxxx \
  -e FASTGPT_MOCK=false \
  -e FASTGPT_CHAT_ID=14736041-f59e-4329-9284-f20475ab7bd3 \
  -e FASTGPT_FILE_URL="https://your-oss/xxx.pdf?signature=..." \
  fastgpt-kotlin-demo:1.0.0
```

---

## 五、日志解读

```
2026-11-17 16:36:28.848  INFO [global-fusion-3] c.h.c.FastGptClient.chat[81] - 请求大模型参数：{"messages":[],"responseChatItemId":"f0851de1-...","appId":"6a85961e6b9786fd6003756a","chatId":"14736041-f59e-4329-9284-f20475ab7bd3","detail":false,"stream":false,"retainDatasetCite":false,"variables":{"file":["http://.../附件2. ...pdf?..."]},"system":"0","cTime":"2026-11-17 16:36:28 Tuesday","business_code":"0"}
2026-11-17 16:36:49.223  INFO [global-fusion-1] c.h.c.FastGptClient.chat[107] - 非结构化提取结果：{"scene":""}
```

* **第 81 行**：真正发出的请求体。`variables.file` 是让 FastGPT 去下载并解析的附件地址；`detail=false`、`stream=false` 表示非流式、且不返回数据集引用明细。
* **第 107 行**：非结构化提取结果。从模型自由文本里依次尝试「```json 代码块 → 裸 JSON → 正则 `"scene"\s*:\s*"([^"]*)"`」，都取不到就是 `{"scene":""}`，与线上日志完全一致（说明该次调用没有抽到场景信息）。
* **关于线程号**：线上两条日志分别落在 `global-fusion-3` 与 `global-fusion-1`，是因为它们来自两次并发的独立调用；demo 里同一次调用的「请求 / 提取」两个阶段在同一个 `global-fusion-N` 线程上顺序打印。`FASTGPT_CONCURRENCY=3` 时可以看到 `global-fusion-1/2/3` 三个线程并发调用，形态与线上一致。
* **协程与线程**：`chat()` 是 `suspend` 函数，HTTP 等待期间挂起而不占线程；但调度器仍是固定的 `global-fusion-N` 线程池，因此日志里的线程名可预期。

---

## 六、本地开发（不用 Docker）

```bash
# 需要 JDK 21+
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew installDist
./build/install/fastgpt-kotlin-demo/bin/fastgpt-kotlin-demo
```

或直接用 `./gradlew run`（MOCK 模式下无需任何密钥）。

---

## 七、说明与注意事项

1. **OSS 附件地址带签名且有有效期**：日志里的 `?Expires=1794990988&Signature=...` 过期后，FastGPT 侧会下载失败，MOCK 模式不受影响。
2. **FastGPT 会忽略不认识的字段**：`appId` / `responseChatItemId` / `cTime` / `business_code` 属于业务侧透传字段，服务端会直接忽略，但保留它们才能保证「请求参数」日志与线上逐字节可比。
3. **请求体不走 ContentNegotiation 序列化**：Ktor 的 `setBody(TextContent(body, ContentType.Application.Json))` 直接发送原始 JSON，避免 String 被内容协商插件二次序列化（会多出一层引号）。
4. **MOCK 模式只是链路演示**：返回固定的自然语言 + 空 `scene`，用于验证日志格式、线程模型和容器运行，不产生真实的大模型调用；耗时用协程 `delay` 模拟，不占线程。
5. **镜像架构**：Dockerfile 运行阶段使用 `--platform=$TARGETPLATFORM`，配合 `docker buildx build --platform linux/amd64` 即可产出 `linux/amd64` 镜像；构建阶段在宿主架构上跑 Gradle，避免 QEMU 模拟导致构建极慢。
