package eu.kanade.tachiyomi.data.download.downloader

import java.util.concurrent.CopyOnWriteArrayList

fun interface ItemProgress {
    fun report(fraction: Float)
}

class ProgressAggregator(
    private val onChange: (Int) -> Unit,
) {
    inner class Item(internal val weight: Double) : ItemProgress {
        @Volatile
        internal var fraction = 0f

        override fun report(fraction: Float) {
            this.fraction = maxOf(this.fraction, fraction.coerceIn(0f, 1f))
            emit()
        }
    }

    private val items = CopyOnWriteArrayList<Item>()
    private val lock = Any()
    private var last = -1

    fun register(weight: Double): Item = Item(weight).also { items.add(it) }

    private fun emit() {
        val totalWeight = items.sumOf { it.weight }
        if (totalWeight <= 0.0) return

        val percent = (100 * items.sumOf { it.weight * it.fraction } / totalWeight).toInt().coerceIn(0, 100)

        synchronized(lock) {
            if (percent > last) {
                last = percent
                onChange(percent)
            }
        }
    }
}
