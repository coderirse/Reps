package io.github.coderirse.reps.core

/** Duration formatting shared by the home restore dialog and the result page. */
object TimeFormat {

    /** Under an hour "MM:SS"; at or above "H:MM:SS" so 90min doesn't read "90:00". */
    fun duration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
