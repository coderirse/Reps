package io.github.coderirse.reps.ui.components

import android.content.Context
import io.github.coderirse.reps.R
import io.github.coderirse.reps.data.net.ApiException
import java.io.IOException

/**
 * 把底层异常映射成用户能看懂的中文提示。[ApiException] 的 message 本身就是
 * 中文（网络层构造时写死），其余异常按类型归并：
 * - IO 类（UnknownHost / Connect / SocketTimeout 等）→ 统一的「网络连接失败」；
 * - 其他未知异常 → 调用方给的兜底文案，**不透传** `error.message`——那可能是
 *   英文的 "Unable to resolve host"，直接展示只会让人困惑。
 *
 * 调用方必须先单独重抛 [kotlinx.coroutines.CancellationException]，这里不做
 * （也不该做）这件事——它不是错误，是协程取消的正常机制。
 */
fun userFacingMessage(context: Context, error: Throwable, fallbackRes: Int): String = when {
    error is ApiException -> error.message?.takeIf { it.isNotBlank() } ?: context.getString(fallbackRes)
    error is IOException -> context.getString(R.string.error_network)
    else -> context.getString(fallbackRes)
}
