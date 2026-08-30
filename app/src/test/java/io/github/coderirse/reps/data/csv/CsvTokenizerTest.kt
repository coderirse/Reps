package io.github.coderirse.reps.data.csv

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTokenizerTest {

    private val parser = CsvQuestionParser()

    @Test
    fun `basic rows split on commas and newlines`() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("1", "2")),
            parser.parseCsvRows("a,b\n1,2"),
        )
    }

    @Test
    fun `crlf line endings are handled`() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("1", "2")),
            parser.parseCsvRows("a,b\r\n1,2"),
        )
    }

    @Test
    fun `quoted field keeps commas and newlines`() {
        assertEquals(
            listOf(listOf("a", "line1\nline2,with comma")),
            parser.parseCsvRows("a,\"line1\nline2,with comma\""),
        )
    }

    @Test
    fun `escaped quotes inside quoted field`() {
        // CSV content: "he said ""hi"""  ->  he said "hi"
        assertEquals(
            listOf(listOf("he said \"hi\"")),
            parser.parseCsvRows("\"he said \"\"hi\"\"\""),
        )
    }

    @Test
    fun `trailing empty field is preserved`() {
        assertEquals(
            listOf(listOf("a", "b", "")),
            parser.parseCsvRows("a,b,"),
        )
    }

    @Test
    fun `ragged rows are returned as-is for row-level validation`() {
        assertEquals(
            listOf(listOf("h1", "h2", "h3"), listOf("only-one")),
            parser.parseCsvRows("h1,h2,h3\nonly-one"),
        )
    }

    @Test
    fun `blank trailing newline does not create empty row`() {
        assertEquals(
            listOf(listOf("a", "b")),
            parser.parseCsvRows("a,b\n"),
        )
    }
}
