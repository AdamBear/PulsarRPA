package ai.platon.pulsar.common.collect

import ai.platon.pulsar.common.Priority13
import ai.platon.pulsar.common.readable
import java.time.Duration
import java.time.Instant

/**
 * The data collector interface
 * */
interface DataCollector<T> {
    var name: String

    /**
     * The collector cache capacity. At most [capacity] items can be collected to the cache from the source
     * */
    val capacity: Int
    val size: Int
    val estimatedSize: Int
    val collectCount: Int
    val collectedCount: Int
    val firstCollectTime: Instant
    val lastCollectedTime: Instant
    val collectTime: Duration
    fun hasMore(): Boolean = false
    fun collectTo(element: T, sink: MutableList<T>): Int
    fun collectTo(index: Int, element: T, sink: MutableList<T>): Int
    fun collectTo(sink: MutableList<T>): Int
    fun collectTo(index: Int, sink: MutableList<T>): Int
}

interface PriorityDataCollector<T> : DataCollector<T>, Comparable<PriorityDataCollector<T>> {
    val priority: Int
    override fun compareTo(other: PriorityDataCollector<T>) = priority - other.priority
}

abstract class AbstractDataCollector<E> : DataCollector<E> {
    companion object {
        const val DEFAULT_CAPACITY = 1000
    }

    override var name: String = "DC"
    override val capacity: Int = DEFAULT_CAPACITY
    override val size: Int get() = 0
    override val estimatedSize: Int = 0

    /**
     * The total count of collect attempt
     * */
    override var collectCount: Int = 0

    /**
     * The total collected count
     * */
    override var collectedCount: Int = 0

    override var firstCollectTime = Instant.EPOCH

    override var lastCollectedTime = Instant.EPOCH

    override val collectTime: Duration get() = Duration.between(firstCollectTime, lastCollectedTime)

    override fun collectTo(element: E, sink: MutableList<E>): Int {
        return collectTo(sink.size - 1, element, sink)
    }

    override fun collectTo(index: Int, element: E, sink: MutableList<E>): Int {
        sink.add(index, element)
        return 1
    }

    override fun collectTo(index: Int, sink: MutableList<E>): Int {
        val list = mutableListOf<E>()
        collectTo(list)
        sink.addAll(index, list)
        return list.size
    }

    override fun toString(): String {
        val elapsedTime = Duration.between(firstCollectTime, lastCollectedTime)
        val elapsedSeconds = elapsedTime.seconds.coerceAtLeast(1)
        return String.format("%s - collected %s/%s/%s/%s in %s, remaining %s/%s, collect time: %s -> %s",
            name,
            collectedCount,
            String.format("%.2f", 1.0 * collectedCount / elapsedSeconds),
            collectCount,
            String.format("%.2f", 1.0 * collectCount / elapsedSeconds),
            elapsedTime.readable(),
            size, estimatedSize,
            firstCollectTime, lastCollectedTime
        )
    }

    protected fun beforeCollect() {
        if (firstCollectTime == Instant.EPOCH) {
            firstCollectTime = Instant.now()
        }

        ++collectCount
    }

    protected fun afterCollect(collected: Int): Int {
        collectedCount += collected

        if (collected > 0) {
            lastCollectedTime = Instant.now()
        }

        return collected
    }
}

abstract class AbstractPriorityDataCollector<T>(
    override val priority: Int = Priority13.NORMAL.value,
    override val capacity: Int = DEFAULT_CAPACITY,
) : AbstractDataCollector<T>(), PriorityDataCollector<T> {
    constructor(priority: Priority13) : this(priority.value)

    override var name: String = "PriorityDC"

    override fun toString(): String {
        val elapsedTime = Duration.between(firstCollectTime, lastCollectedTime)
        val elapsedSeconds = elapsedTime.seconds.coerceAtLeast(1)
        return String.format("%s(%d) - collected %s/%s/%s/%s in %s, remaining %s/%s, collect time: %s -> %s",
            name, priority,
            collectedCount,
            String.format("%.2f", 1.0 * collectedCount / elapsedSeconds),
            collectCount,
            String.format("%.2f", 1.0 * collectCount / elapsedSeconds),
            elapsedTime.readable(),
            size, estimatedSize,
            firstCollectTime, lastCollectedTime
        )
    }
}
