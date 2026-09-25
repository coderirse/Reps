package io.github.coderirse.reps.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 契约测试：样本取自服务端 /api/reps/ 接口的真实响应（见 docs/DEVELOPMENT.md §13）。
 *
 * 作用是钉住字段名——后端改字段名时应该在这里失败，而不是让客户端静默拿到默认值，
 * 在用户手机上表现为「云端题库空空如也」这种查不出原因的故障。
 */
class RepsApiDtoTest {

    /** 与运行时同一份 Json 配置（ignoreUnknownKeys / explicitNulls 等）。 */
    private val json = RepsNet.json

    @Test
    fun `parses bank list with one bank`() {
        val raw = """{"updatedAt":1790311308319,"banks":[{"id":"metalwork","name":"金工实习","description":"工程训练中心复习题集","questionCount":390,"sizeBytes":52700,"sha256":"3dffa7f373bc704bfb9dd72f8b2cda74fb88f18a9146d82f3bbacb5b0e615a21","updatedAt":1790311308319,"url":"http://112.125.88.178/api/reps/banks/metalwork/file"}]}"""

        val parsed = json.decodeFromString(CloudBankListDto.serializer(), raw)

        assertEquals(1790311308319L, parsed.updatedAt)
        assertEquals(1, parsed.banks.size)
        val bank = parsed.banks.single()
        assertEquals("metalwork", bank.id)
        assertEquals("金工实习", bank.name)
        assertEquals(390, bank.questionCount)
        assertEquals(52700L, bank.sizeBytes)
        assertEquals(64, bank.sha256.length)
        assertTrue(bank.url.startsWith("http://112.125.88.178/api/reps/banks/"))
    }

    @Test
    fun `parses empty bank list`() {
        val parsed = json.decodeFromString(CloudBankListDto.serializer(), """{"updatedAt":0,"banks":[]}""")

        assertEquals(0L, parsed.updatedAt)
        assertTrue(parsed.banks.isEmpty())
    }

    @Test
    fun `parses app latest`() {
        val raw = """{"versionCode":7,"versionName":"1.1.1","changelog":"1. 新增云端题库","force":false,"size":3211264,"url":"http://112.125.88.178/downloads/reps/reps-1.1.1-release.apk","apkSha256":"abc123"}"""

        val info = json.decodeFromString(AppVersionDto.serializer(), raw)

        assertEquals(7, info.versionCode)
        assertEquals("1.1.1", info.versionName)
        assertEquals(3211264L, info.size)
        assertEquals("abc123", info.apkSha256)
        assertFalse(info.force)
    }

    @Test
    fun `missing optional fields fall back to defaults`() {
        // 服务端早期版本没有 apkSha256：必须降级成空串而不是解析失败，
        // 否则一个新字段就能让更新弹窗整个打不开。
        val info = json.decodeFromString(
            AppVersionDto.serializer(),
            """{"versionCode":6,"versionName":"1.1.0","url":"http://example.com/reps.apk"}""",
        )

        assertEquals("", info.apkSha256)
        assertEquals(0L, info.size)
        assertFalse(info.force)
    }

    @Test
    fun `unknown fields are ignored so the server can add fields first`() {
        // 后端先发新字段、客户端后升级：老客户端必须能继续解析。
        val parsed = json.decodeFromString(
            CloudBankListDto.serializer(),
            """{"updatedAt":1,"somethingNew":true,"banks":[{"id":"a","name":"A","futureField":42,"url":"u"}]}""",
        )

        assertEquals("a", parsed.banks.single().id)
    }
}
