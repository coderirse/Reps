package io.github.coderirse.reps.data.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.Charset

class EncodingDetectorTest {

    private val gb = Charset.forName("GB18030")

    @Test
    fun `utf8 without bom is detected`() {
        val bytes = "哲学的基本问题是,思维与存在".toByteArray(Charsets.UTF_8)
        assertEquals(Charsets.UTF_8, EncodingDetector.detect(bytes))
    }

    @Test
    fun `utf8 with bom is detected`() {
        val text = "id,content,type\n1,哲学,single"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            text.toByteArray(Charsets.UTF_8)
        assertEquals(Charsets.UTF_8, EncodingDetector.detect(bytes))
    }

    @Test
    fun `utf16le with bom is detected`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "内容".toByteArray(Charsets.UTF_16LE)
        assertEquals(Charsets.UTF_16LE, EncodingDetector.detect(bytes))
    }

    @Test
    fun `gbk encoded chinese without bom falls back to gb18030`() {
        // Excel's "CSV (GBK)" export: GBK is a subset of GB18030.
        val bytes = "题干,选项A,答案\n哲学的基本问题,思维与存在,A".toByteArray(gb)
        assertEquals(gb, EncodingDetector.detect(bytes))
    }

    @Test
    fun `ascii is detected as utf8`() {
        assertEquals(Charsets.UTF_8, EncodingDetector.detect("id,content\n1,hello".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `undecodable garbage returns null without crossCheck`() {
        // 0x81 0x40 sequences chosen so neither UTF-8 nor the plausibility
        // check passes; GB18030 technically decodes them, producing mojibake.
        val bytes = byteArrayOf(0x81.toByte(), 0x40.toByte(), 0x30, 0x81.toByte())
        assertNull(EncodingDetector.detect(bytes))
    }

    @Test
    fun `crossCheck can rescue unsupported encodings`() {
        val bytes = byteArrayOf(0x81.toByte(), 0x40.toByte(), 0x30, 0x81.toByte())
        assertEquals(Charset.forName("Shift_JIS"), EncodingDetector.detect(bytes) { Charset.forName("Shift_JIS") })
    }
}
