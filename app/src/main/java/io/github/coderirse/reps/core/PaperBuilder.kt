package io.github.coderirse.reps.core

import io.github.coderirse.reps.data.db.entity.QuestionType
import java.util.Random

/** Quotas for a CUSTOM practice session; unused types use quota 0. */
data class CustomQuota(
    val single: Int = 0,
    val multi: Int = 0,
    val judge: Int = 0,
) {
    val total: Int get() = single + multi + judge
}

enum class CustomOrder { SEQUENTIAL, RANDOM }

/**
 * Builds a CUSTOM paper from per-type id pools (each already sorted by
 * orderIndex). Sequential takes from the head; random does seeded per-type
 * sampling then a seeded shuffle of the merged list.
 */
object PaperBuilder {

    fun pickIds(
        pools: Map<String, List<Long>>,
        quota: CustomQuota,
        order: CustomOrder,
        seed: Long,
    ): List<Long> {
        require(quota.total > 0) { "Custom paper needs at least one question" }
        fun pool(type: String): List<Long> = pools[type].orEmpty()
        require(quota.single <= pool(QuestionType.SINGLE).size) { "Not enough single-choice questions" }
        require(quota.multi <= pool(QuestionType.MULTI).size) { "Not enough multi-choice questions" }
        require(quota.judge <= pool(QuestionType.JUDGE).size) { "Not enough judge questions" }

        val picked = if (order == CustomOrder.RANDOM) {
            val random = Random(seed)
            fun takeRandom(type: String, count: Int): List<Long> {
                val source = pool(type).toMutableList()
                return List(count) { source.removeAt(random.nextInt(source.size)) }
            }
            takeRandom(QuestionType.SINGLE, quota.single) +
                takeRandom(QuestionType.MULTI, quota.multi) +
                takeRandom(QuestionType.JUDGE, quota.judge)
        } else {
            pool(QuestionType.SINGLE).take(quota.single) +
                pool(QuestionType.MULTI).take(quota.multi) +
                pool(QuestionType.JUDGE).take(quota.judge)
        }

        return if (order == CustomOrder.RANDOM) Shuffle.shuffled(picked, seed) else picked
    }
}
