package io.github.coderirse.reps.data.csv

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset

/**
 * CSV file encoding detection, ordered per docs/DEVELOPMENT.md section 5.1:
 * BOM -> strict UTF-8 -> GB18030 (superset of GBK/GB2312) with plausibility
 * check -> optional cross-check sniffer (device passes android.icu-backed
 * CharsetDetector; JVM unit tests pass null) -> give up.
 *
 * Kept free of Android framework classes so the whole pipeline is JVM-testable.
 */
object EncodingDetector {

    /** Fraction of plausible chars (CJK/ASCII/common punctuation) a GB18030 decode must reach. */
    private const val PLAUSIBILITY_THRESHOLD = 0.5

    fun detect(bytes: ByteArray, crossCheck: ((ByteArray) -> Charset?)? = null): Charset? {
        bomCharset(bytes)?.let { return it }
        if (isStrictDecodable(bytes, Charsets.UTF_8)) return Charsets.UTF_8

        val gb = Charset.forName("GB18030")
        if (isStrictDecodable(bytes, gb) && isPlausibleText(decode(bytes, gb))) {
            return gb
        }
        return crossCheck?.invoke(bytes)
    }

    /**
     * Device-side cross-check wired in production. Uses reflection because
     * newer android.jar distributions no longer ship android.icu classes at
     * compile time; the class is present on all runtime devices (API 24+).
     */
    fun icuCrossCheck(bytes: ByteArray): Charset? = try {
        val detectorClass = Class.forName("android.icu.text.CharsetDetector")
        val detector = detectorClass.getDeclaredConstructor().newInstance()
        detectorClass.getMethod("setText", ByteArray::class.java).invoke(detector, bytes)
        val match = detectorClass.getMethod("detect").invoke(detector) ?: return null
        val name = match.javaClass.getMethod("getName").invoke(match) as? String ?: return null
        if (Charset.isSupported(name)) Charset.forName(name) else null
    } catch (_: Throwable) {
        null
    }

    private fun bomCharset(bytes: ByteArray): Charset? = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> Charsets.UTF_8
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
        else -> null
    }

    private fun isStrictDecodable(bytes: ByteArray, charset: Charset): Boolean = try {
        decode(bytes, charset); true
    } catch (_: CharacterCodingException) {
        false
    }

    private fun decode(bytes: ByteArray, charset: Charset): String {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }

    /** GB18030 accepts nearly any byte stream; reject decodes that look like mojibake. */
    private fun isPlausibleText(text: String): Boolean {
        val sample = text.take(4000)
        if (sample.isEmpty()) return true
        val plausible = sample.count { it.isPlausibleChar() }
        val nonWhitespace = sample.count { !it.isWhitespace() }
        if (nonWhitespace == 0) return true
        return plausible.toDouble() / sample.length >= PLAUSIBILITY_THRESHOLD
    }

    private fun Char.isPlausibleChar(): Boolean =
        code in 0x20..0x7E ||                    // ASCII printable
            code in 0x4E00..0x9FFF ||            // CJK Unified Ideographs
            code in 0x3000..0x303F ||            // CJK punctuation
            code in 0xFF00..0xFFEF ||            // full-width forms
            code in 0x2000..0x206F ||            // general punctuation
            code in 0x3400..0x4DBF ||            // CJK Extension A
            code == 0x00B7 || code == '…'.code || code == '—'.code
}
