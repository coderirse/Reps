package io.github.coderirse.reps.data.csv

import io.github.coderirse.reps.data.db.entity.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvQuestionParserTest {

    private val parser = CsvQuestionParser()

    // 12 columns: id,content,type,option_a..option_e,correct_answer,explanation,category,chapter
    private val header =
        "id,content,type,option_a,option_b,option_c,option_d,option_e,correct_answer,explanation,category,chapter"

    @Test
    fun `parses single judge and multi rows`() {
        val csv = buildString {
            appendLine(header)
            appendLine("1,哲学的基本问题是,single,思维与存在,理论与实践,,,,A,解析1,马原,第一章")
            appendLine("2,哲学是科学吗,judge,,,,,,True,Bogus,马原,第一章")
            appendLine("3,下列属于唯物的有,multi,古代朴素,近代形而上学,辩证,唯心,,\"a,c\",解析3,马原,第二章")
        }
        val result = parser.parse(csv)
        assertNull(result.headerError)
        assertEquals(3, result.questions.size)

        val single = result.questions[0]
        assertEquals(QuestionType.SINGLE, single.type)
        assertEquals("A", single.correctAnswer)
        assertEquals("哲学的基本问题是", single.content)

        val judge = result.questions[1]
        assertEquals(QuestionType.JUDGE, judge.type)
        assertEquals("对", judge.correctAnswer)

        val multi = result.questions[2]
        assertEquals(QuestionType.MULTI, multi.type)
        assertEquals("A,C", multi.correctAnswer)
        assertEquals(3, result.totalRows)
    }

    @Test
    fun `quoted field with embedded comma and newline survives`() {
        val csv = buildString {
            appendLine(header)
            appendLine("1,\"题干，含逗号\n第二行\",single,甲,乙,,,,B,解析,分类,章")
        }
        val result = parser.parse(csv)
        assertEquals(1, result.questions.size)
        assertEquals("题干，含逗号\n第二行", result.questions[0].content)
        assertEquals(1, result.totalRows)
    }

    @Test
    fun `header is case and whitespace insensitive`() {
        val csv = buildString {
            appendLine("Content , TYPE , Correct_Answer , Option_A , Option_B")
            appendLine("题干,single,A,甲")
        }
        val result = parser.parse(csv)
        assertNull(result.headerError)
        assertEquals(1, result.questions.size)
        assertEquals("A", result.questions[0].correctAnswer)
    }

    @Test
    fun `missing required column reports header error`() {
        val csv = "id,content,option_a\n1,题干,A"
        val result = parser.parse(csv)
        assertTrue(result.headerError!!.contains("correct_answer"))
        assertFalse(result.canImport)
    }

    @Test
    fun `unsupported type counts as skipped not error`() {
        val csv = buildString {
            appendLine(header)
            appendLine("1,填空题,blank,,,,,,参考答案,,")
            appendLine("2,正常题,single,甲,乙,,,,A,,,")
        }
        val result = parser.parse(csv)
        assertEquals(1, result.questions.size)
        assertEquals(1, result.skippedUnsupported)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `empty content and bad answers produce row errors`() {
        val csv = buildString {
            appendLine(header)
            appendLine("1,,single,甲,乙,,,,A,,,")
            appendLine("2,题干,single,甲,乙,,,,H,,,")
            appendLine("3,题干,judge,,,,,,也许,,,")
            appendLine("4,题干,multi,甲,乙,,,,A,,,")
        }
        val result = parser.parse(csv)
        assertEquals(0, result.questions.size)
        assertEquals(4, result.errors.size)
        assertEquals(2, result.errors[0].lineNo)
        assertEquals(5, result.errors[3].lineNo)
    }

    @Test
    fun `duplicate id is rejected`() {
        val csv = buildString {
            appendLine(header)
            appendLine("1,题干一,single,甲,乙,,,,A,,,")
            appendLine("1,题干二,single,甲,乙,,,,B,,,")
        }
        val result = parser.parse(csv)
        assertEquals(1, result.questions.size)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].reason.contains("重复"))
    }

    @Test
    fun `missing id falls back to row number`() {
        val csv = buildString {
            appendLine(header.removePrefix("id,"))
            appendLine("题干一,single,甲,乙,,,,A,,,")
            appendLine("题干二,single,甲,乙,,,,B,,,")
        }
        val result = parser.parse(csv)
        assertEquals(listOf(1, 2), result.questions.map { it.orderIndex })
    }

    @Test
    fun `preview is capped`() {
        val smallParser = CsvQuestionParser(maxPreview = 2)
        val csv = buildString {
            appendLine(header)
            repeat(5) { i -> appendLine("${i + 1},题$i,single,甲,乙,,,,A,,,") }
        }
        val result = smallParser.parse(csv)
        assertEquals(5, result.questions.size)
        assertEquals(2, result.preview.size)
    }

    @Test
    fun `utf8 bom before header is tolerated`() {
        // Excel's default UTF-8 export carries a BOM; String(bytes, charset)
        // does not consume it, so the parser must.
        val csv = "\uFEFF" + header + "\n1,题干,single,甲,乙,,,,A,,,"
        val result = parser.parse(csv)
        assertNull(result.headerError)
        assertEquals(1, result.questions.size)
        assertEquals("题干", result.questions[0].content)
    }

    @Test
    fun `error line numbers account for blank rows`() {
        val csv = buildString {
            appendLine(header)
            appendLine("1,第一题,single,甲,乙,,,,A,,,")
            appendLine("")
            appendLine("2,,single,甲,乙,,,,A,,,") // blank content at file row 4
        }
        val result = parser.parse(csv)
        assertEquals(1, result.questions.size)
        assertEquals(1, result.errors.size)
        assertEquals(4, result.errors[0].lineNo)
    }
}
