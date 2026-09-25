package io.github.coderirse.reps.data.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.io.File
import java.security.MessageDigest

/**
 * 下载工具（更新包 + 云端题库）。落在 `cache/downloads`，通过 FileProvider
 * (见 res/xml/file_paths.xml) 交给系统安装器或应用内导入流程。
 *
 * 全部先写 `.part` 再改名——中断的下载不会留下半截文件被后续流程当成完整文件；
 * finally 里兜底清理 `.part`，弱网反复重试不会攒垃圾。
 */
object Downloads {

    private const val DIR = "downloads"

    /** 服务端/文件名不可信，先清掉路径分隔符，防止写出目标目录。 */
    private fun sanitize(name: String): String =
        name.replace(Regex("[/\\\\]"), "_").trim().ifBlank { "download" }

    /**
     * 只允许从后端服务器下载。云端清单里的 [CloudBankDto.url] 与更新元数据里的
     * [AppVersionDto.url] 都是服务端下发的内容，不能盲信——万一清单被写进别的主机，
     * 那台主机不在 network_security_config 的明文白名单里，静默放行只会留下一个
     * 看不出原因的「下载失败」。这里显式校验 host 并给出明确报错。
     */
    private fun checkHost(url: String) {
        val expected = RepsNet.BASE_URL.toHttpUrlOrNull()?.host
        val actual = url.toHttpUrlOrNull()
        when {
            actual == null -> throw ApiException(0, "下载地址无效")
            expected != null && actual.host != expected ->
                throw ApiException(0, "下载地址与服务器不符，已拒绝下载")
        }
    }

    /**
     * 下载到缓存目录。给了 [sha256] 就做完整性校验，对不上直接丢弃并报错。
     * 注意：摘要与文件走同一条 HTTP 信道，它只能防**传输损坏与截断**（以及
     * 与服务端声明值比对出的落盘错误），防不了同信道的中间人篡改——那需要
     * HTTPS（备案后切换）或服务端对文件做私钥签名、客户端验签。
     *
     * [contentLength] 未知（chunked 响应）时回退用 [expectedBytes] 估算；两者都
     * 拿不到时 [onProgress] 收到 [PROGRESS_UNKNOWN]，UI 应显示不确定进度。
     */
    suspend fun toCache(
        context: Context,
        url: String,
        fileName: String,
        sha256: String = "",
        expectedBytes: Long = 0,
        onProgress: ((progress: Float, bytesRead: Long, totalBytes: Long) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        checkHost(url)

        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val name = sanitize(fileName)
        val target = File(dir, name)
        val tmp = File(dir, "$name.part")

        try {
            val request = Request.Builder().url(url).get().build()
            RepsNet.client.newCall(request).executeAsync().use { resp ->
                if (!resp.isSuccessful) throw ApiException(resp.code, "下载失败 (${resp.code})")
                val body = resp.body ?: throw ApiException(resp.code, "下载失败：响应为空")
                val total = listOf(body.contentLength(), expectedBytes).firstOrNull { it > 0 } ?: -1L
                var read = 0L
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                            read += count
                            onProgress?.invoke(
                                if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else PROGRESS_UNKNOWN,
                                read,
                                total,
                            )
                        }
                    }
                }
            }

            if (sha256.isNotBlank() && !sha256Of(tmp).equals(sha256, ignoreCase = true)) {
                throw ApiException(0, "文件校验失败，已丢弃损坏的下载")
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            target
        } finally {
            // 成功路径上 tmp 已改名/删除；走到这里还存在的只剩中断留下的半截文件
            if (tmp.exists()) tmp.delete()
        }
    }

    /** [toCache] 的进度回调在总大小未知时发出的哨兵值。 */
    const val PROGRESS_UNKNOWN = -1f

    /** 清掉上一轮更新留下的 APK（安装器接管后就不再需要了）。 */
    fun clearCachedApks(context: Context) {
        File(context.cacheDir, DIR).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".apk") }
            ?.forEach { it.delete() }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /**
     * 调起系统安装器。Android 8+ 需要「安装未知应用」授权，没有权限时跳到设置页
     * 并返回 false，由调用方提示用户；用户授权返回后 UpdateViewModel 会自动续装。
     */
    fun installApk(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return false
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        }.getOrDefault(false)
    }
}
