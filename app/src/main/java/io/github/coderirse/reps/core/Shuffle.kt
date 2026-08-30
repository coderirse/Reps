package io.github.coderirse.reps.core

import java.util.Random

/** Deterministic helpers shared by paper building and tests. */
object Shuffle {

    /** Seeded Fisher–Yates; same input + same seed -> same order. */
    fun <T> shuffled(items: List<T>, seed: Long): List<T> {
        val result = items.toMutableList()
        val random = Random(seed)
        for (i in result.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            result[i] = result[j].also { result[j] = result[i] }
        }
        return result
    }
}
