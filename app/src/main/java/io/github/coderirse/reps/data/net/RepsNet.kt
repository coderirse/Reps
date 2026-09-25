package io.github.coderirse.reps.data.net

import android.util.Log
import io.github.coderirse.reps.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.coroutines.executeAsync
import java.util.concurrent.TimeUnit

/** 服务端返回非 2xx 时抛出，[code] 是 HTTP 状态码。 */
class ApiException(val code: Int, message: String) : Exception(message)

/**
 * 全应用**唯一**发起网络请求的地方——这条边界由 `app/build.gradle.kts` 的
 * `verifyNetworkContainment` 门禁强制（其他包出现 okhttp/java.net 调用即构建失败）。
 *
 * Reps 是**只读客户端**：只下载云端题库清单/CSV 与更新元数据，不向服务器发送
 * 任何用户数据。题库、进度、错题、笔记全部只存本机——这是 PRODUCT.md 里对用户的
 * 承诺，也是这套后端只提供 GET 接口的原因。
 */
object RepsNet {

    /**
     * 后端目前只能用公网 IP 直连：域名 api.caeamer.com 未完成 ICP 备案，走域名的
     * 流量会被云厂商拦截（HTTP 403 拦截页 / TLS 握手被 reset，详见
     * docs/DEVELOPMENT.md「服务端接入」）。备案完成后改成域名 + HTTPS，并同步收紧
     * res/xml/network_security_config.xml 里的明文白名单即可。
     */
    const val BASE_URL = "http://112.125.88.178"

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // 题库 CSV 可能几十 KB 到几 MB，给宽松的整体超时
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply { if (BuildConfig.DEBUG) addInterceptor(DebugLogInterceptor()) }
            .build()
    }

    /** GET 一个 JSON 端点并反序列化。仅用于本项目自己的 /api/reps/ 只读接口。 */
    suspend fun <T> get(deserializer: DeserializationStrategy<T>, path: String): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(BASE_URL + path).get().build()
            // await() 在协程取消时会中断请求，不像 execute() 那样占着线程跑完
            val response = client.newCall(request).executeAsync()
            response.use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw ApiException(resp.code, "请求失败 (${resp.code})")
                }
                runCatching { json.decodeFromString(deserializer, text) }.getOrElse {
                    throw ApiException(resp.code, "返回数据解析失败")
                }
            }
        }

    /**
     * 极简请求日志，仅 debug 构建挂载。自己写这十几行是为了不引入
     * logging-interceptor——它是 debugImplementation，release 编译期看不到类，
     * 直接引用会让 release 构建失败。
     */
    private class DebugLogInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            Log.d("RepsNet", "${request.method} ${request.url} -> ${response.code}")
            return response
        }
    }
}
