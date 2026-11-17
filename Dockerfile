# syntax=docker/dockerfile:1.7

# ==========================================================================
# 构建阶段
# --------------------------------------------------------------------------
# 在宿主架构（$BUILDPLATFORM）上执行 Gradle 构建，避免用 QEMU 模拟执行
# Gradle 导致构建极慢；产物是纯 JVM jar，与 CPU 架构无关，可直接 COPY 到
# amd64 的运行阶段。
# ==========================================================================
FROM --platform=$BUILDPLATFORM gradle:8-jdk21 AS builder

WORKDIR /build

# 先只拷贝构建描述文件，依赖解析层可被缓存复用
COPY gradle.properties settings.gradle.kts build.gradle.kts ./
COPY src src

RUN gradle --no-daemon --console=plain installDist -x test

# ==========================================================================
# 运行阶段
# ==========================================================================
FROM --platform=$TARGETPLATFORM eclipse-temurin:21-jre

LABEL org.opencontainers.image.title="fastgpt-kotlin-demo" \
      org.opencontainers.image.description="FastGPT 请求测试 Demo（Kotlin），复现线上 global-fusion 调用日志" \
      org.opencontainers.image.version="1.0.0"

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-Xmx512m -Duser.timezone=Asia/Shanghai" \
    FASTGPT_BASE_URL="https://api.fastgpt.io" \
    FASTGPT_CHAT_ID="14736041-f59e-4329-9284-f20475ab7bd3" \
    FASTGPT_APP_ID="6a85961e6b9786fd6003756a" \
    FASTGPT_SYSTEM="0" \
    FASTGPT_BUSINESS_CODE="0" \
    FASTGPT_CONCURRENCY="3" \
    FASTGPT_TIMEOUT_SECONDS="180" \
    FASTGPT_MOCK="true" \
    FASTGPT_MOCK_DELAY_MS="20000"

WORKDIR /app
COPY --from=builder /build/build/install/fastgpt-kotlin-demo /app
RUN chmod +x /app/bin/fastgpt-kotlin-demo \
    && ln -sf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone

ENTRYPOINT ["/app/bin/fastgpt-kotlin-demo"]
