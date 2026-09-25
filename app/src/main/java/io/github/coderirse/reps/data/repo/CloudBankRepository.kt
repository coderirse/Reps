package io.github.coderirse.reps.data.repo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.coderirse.reps.data.net.CloudBankDto
import io.github.coderirse.reps.data.net.Downloads
import io.github.coderirse.reps.data.net.RepsApi

/**
 * 云端题库：列出服务器上的题库清单，并把选中的 CSV 拉到本地缓存。
 *
 * 下载完**不直接入库**，而是换一个 content:// URI 交给既有的导入预览流程
 * （ImportPreviewScreen）。这样「导入前必须预览」这条产品原则
 * （docs/PRODUCT.md §5.4）对云端题库同样成立——用户仍能改名、看解析报告与
 * 逐行错误、再决定要不要入库，走的也是同一条 CSV 解析管线。
 *
 * 本类自己不发起网络请求，只调用 data/net 里的封装；隐私门禁
 * `verifyNetworkContainment` 管的是网络 API 的分布，不是调用链深度。
 */
class CloudBankRepository(private val context: Context) {

    /** 题库清单。服务端热加载，作者上架后这里立刻能看到，不需要发新版 App。 */
    suspend fun list(): List<CloudBankDto> = RepsApi.banks().banks

    /**
     * 下载题库 CSV 到缓存目录，返回可交给导入流程读取的 URI。
     *
     * [CloudBankDto.sha256] 由服务端下发，`Downloads.toCache` 会校验；对不上就抛错，
     * 绝不把被篡改或截断的文件送进导入流程。
     */
    suspend fun download(bank: CloudBankDto, onProgress: ((Float) -> Unit)? = null): Uri {
        val file = Downloads.toCache(
            context = context,
            url = bank.url,
            fileName = "${bank.id}.csv",
            sha256 = bank.sha256,
            onProgress = onProgress,
        )
        // 缓存目录已在 res/xml/file_paths.xml 里暴露给 FileProvider
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
