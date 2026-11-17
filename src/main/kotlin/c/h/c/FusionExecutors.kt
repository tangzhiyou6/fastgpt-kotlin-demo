package c.h.c

import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * 融合服务线程池（协程调度器）。
 *
 * 线程名形如 `global-fusion-1` / `global-fusion-2` / `global-fusion-3`，与线上日志一致，
 * 这样 demo 打印出来的日志和线上日志能直接对照（日志里的 `[global-fusion-3]` 即线程名）。
 *
 * 固定线程池包装成协程调度器后：协程可以并发、挂起时不占线程，
 * 但实际执行线程仍然固定为这几个 global-fusion-N，日志线程名可预期。
 */
object FusionExecutors {

    private val threadCounter = AtomicInteger(0)

    /**
     * 创建固定大小的融合线程调度器，线程名从 global-fusion-1 开始递增。
     */
    fun newFusionDispatcher(size: Int): ExecutorCoroutineDispatcher {
        val threadFactory = ThreadFactory { runnable ->
            Thread(runnable, "global-fusion-${threadCounter.incrementAndGet()}").apply {
                isDaemon = false
            }
        }
        return Executors.newFixedThreadPool(size, threadFactory).asCoroutineDispatcher()
    }
}
