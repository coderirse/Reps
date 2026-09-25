package io.github.coderirse.reps.data.net

import kotlinx.serialization.Serializable

/** `GET /api/reps/app/latest` 的响应，字段与服务端 `reps-app-version.json` 对齐。 */
@Serializable
data class AppVersionDto(
    val versionCode: Int = 0,
    val versionName: String = "",
    val changelog: String = "",
    val force: Boolean = false,
    val size: Long = 0,
    val url: String = "",
    /** 服务端给的 APK sha256（可为空：老版本声明里没有这个字段）。 */
    val apkSha256: String = "",
)

/** 一个云端题库。`sha256` 用于下载后校验完整性（能查出传输损坏/截断；防不了同信道的中间人，见 Downloads.toCache）。 */
@Serializable
data class CloudBankDto(
    val id: String,
    val name: String,
    val description: String = "",
    val questionCount: Int = 0,
    val sizeBytes: Long = 0,
    val sha256: String = "",
    val updatedAt: Long = 0,
    val url: String = "",
)

/** `GET /api/reps/banks` 的响应。 */
@Serializable
data class CloudBankListDto(
    val updatedAt: Long = 0,
    val banks: List<CloudBankDto> = emptyList(),
)

/**
 * Reps 的全部服务端接口——只有两个，且都是 GET。
 *
 * 管理员在后端 `reps-banks.json` 里登记新题库后，服务端**热加载**（按文件 mtime
 * 失效重读），所以用户下次进来就能看到新题库，不需要发新版 App、也不需要重启服务。
 */
object RepsApi {

    /** 检查更新；失败向上抛 [ApiException]，由调用方决定静默还是提示。 */
    suspend fun appLatest(): AppVersionDto =
        RepsNet.get(AppVersionDto.serializer(), "/api/reps/app/latest")

    /** 云端题库列表。 */
    suspend fun banks(): CloudBankListDto =
        RepsNet.get(CloudBankListDto.serializer(), "/api/reps/banks")
}
