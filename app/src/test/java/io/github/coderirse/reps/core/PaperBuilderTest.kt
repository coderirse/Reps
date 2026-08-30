package io.github.coderirse.reps.core

import io.github.coderirse.reps.data.db.entity.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperBuilderTest {

    // Disjoint id ranges mirror reality: one question belongs to exactly one type.
    private val pools = mapOf(
        QuestionType.SINGLE to (100L..119L).toList(),
        QuestionType.MULTI to (200L..209L).toList(),
        QuestionType.JUDGE to (300L..304L).toList(),
    )

    @Test
    fun `sequential takes from the head of each pool`() {
        val ids = PaperBuilder.pickIds(
            pools,
            CustomQuota(single = 2, multi = 1, judge = 1),
            CustomOrder.SEQUENTIAL,
            seed = 42,
        )
        assertEquals(listOf(100L, 101L, 200L, 300L), ids)
    }

    @Test
    fun `random sampling is deterministic per seed`() {
        val quota = CustomQuota(single = 5, multi = 3, judge = 2)
        val a = PaperBuilder.pickIds(pools, quota, CustomOrder.RANDOM, seed = 7)
        val b = PaperBuilder.pickIds(pools, quota, CustomOrder.RANDOM, seed = 7)
        assertEquals(a, b)
        // Pools are disjoint by construction; verify per-pool counts.
        assertEquals(5, a.count { it in pools.getValue(QuestionType.SINGLE) })
        assertEquals(3, a.count { it in pools.getValue(QuestionType.MULTI) })
        assertEquals(2, a.count { it in pools.getValue(QuestionType.JUDGE) })
    }

    @Test
    fun `random respects per-pool size limits`() {
        val ids = PaperBuilder.pickIds(
            pools,
            CustomQuota(judge = 5),
            CustomOrder.RANDOM,
            seed = 1,
        )
        assertEquals(5, ids.size)
        assertTrue(ids.all { it in pools.getValue(QuestionType.JUDGE) })
    }

    @Test
    fun `oversized quota throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            PaperBuilder.pickIds(pools, CustomQuota(judge = 6), CustomOrder.SEQUENTIAL, 0)
        }
    }

    @Test
    fun `empty paper throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            PaperBuilder.pickIds(pools, CustomQuota(), CustomOrder.SEQUENTIAL, 0)
        }
    }
}
