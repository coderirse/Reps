package io.github.coderirse.reps.data.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.io.File
import java.security.MessageDigest

/**
 * 下载工具（更新包 + 云端题库）。落在 `cache/downloads`，通过 FileProvider
 * (见 res/xml/file_paths.xml) 交给系统安装器或应用内导入流程。
 *
 * 全部先写 `.part` 再改名——中断的下载不会留下半截文件被后续流程当成完整文件。
 */
object Downloads {

    private const val DIR = "downloads"

    /** 服务端/文件名不可信，先清掉路径分隔符，防止写出目标目录。 */
    private fun sanitize(name: String): String =
        name.replace(Regex("[/\\\\]"), "_").trim().ifBlank { "download" }

    /**
     * 下载到缓存目录。给了 [sha256] 就做完整性校验，对不上直接丢弃并报错——
     * 在明文 HTTP 下这是防止内容被中间人篡改的唯一手段。
     */
    suspend fun toCache(
        context: Context,
        url: String,
        fileName: String,
        sha256: String = "",
        onProgress: ((Float) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val name = sanitize(fileName)
        val target = File(dir, name)
        val tmp = File(dir, "$name.part")

        val request = Request.Builder().url(url).get().build()
        RepsNet.client.newCall(request).executeAsync().use { resp ->
            if (!resp.isSuccessful) throw ApiException(resp.code, "下载失败 (${resp.code})")
            val body = resp.body ?: throw ApiException(resp.code, "下载失败：响应为空")
            val total = body.contentLength().coerceAtLeast(1L)
            var read = 0L
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                        read += count
                        onProgress?.invoke((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }

        if (sha256.isNotBlank() && !sha256Of(tmp).equals(sha256, ignoreCase = true)) {
            tmp.delete()
            throw ApiException(0, "文件校验失败，已丢弃损坏的下载")
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        target
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
     * 并返回 false，由调用方提示用户。
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
