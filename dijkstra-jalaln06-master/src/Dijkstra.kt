package dijkstra

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.ReentrantLock
import java.util.*
import java.util.concurrent.Phaser
import java.util.concurrent.ThreadLocalRandom
import kotlin.Comparator
import kotlin.concurrent.thread

private val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> Integer.compare(o1!!.distance, o2!!.distance) }

// Returns `Integer.MAX_VALUE` if a path has not been found.
fun shortestPathParallel(start: Node) {
    val workers = Runtime.getRuntime().availableProcessors()
    // The distance to the start node is `0`
    start.distance = 0
    // Create a priority (by distance) queue and add the start node into it
    val q = MultiPriorityQueue(workers, NODE_DISTANCE_COMPARATOR) // TODO replace me with a multi-queue based PQ!
    q.insert(start)
    // Run worker threads and wait until the total work is done
    val onFinish = Phaser(workers + 1) // `arrive()` should be invoked at the end by each worker
    repeat(workers) {
        thread {
            while (!q.isEmpty()) {
                val v: Node = q.poll() ?: continue
                for (e in v.outgoingEdges) {
                    do {
                        val distance = e.to.distance
                        val newDistance = v.distance + e.weight
                        if (e.to.updateDistIfLower(distance,newDistance)) {
                            q.insert(e.to)
                            break
                        }
                    } while (distance > newDistance)
                }
            }
            onFinish.arrive()
        }
    }
    onFinish.arriveAndAwaitAdvance()
}

private fun Node.updateDistIfLower(distance: Int, newDistance: Int): Boolean {
    return distance > newDistance && this.casDistance(distance, newDistance)
}


class MultiPriorityQueue(val workers: Int, val comp: Comparator<Node>) {
    val q: MutableList<LockablePriorityQueue<Node>> = Collections.nCopies(workers, LockablePriorityQueue(comp))
    val size = atomic(0)


    fun insert(element: Node) {
        while (true) {
            val idx = ThreadLocalRandom.current().nextInt(workers)
            val queue = q.get(idx)
            if (queue.lock.tryLock()) {
                try {
                    queue.queue.add(element)
                    size.incrementAndGet()
                } finally {
                    queue.lock.unlock()
                }
                return
            }
        }
    }

    fun poll(): Node? {
        while (true) {
            val idx1 = ThreadLocalRandom.current().nextInt(workers)
            val idx2 = ThreadLocalRandom.current().nextInt(workers)
            val queue1 = q.get(idx1)
            val queue2 = q.get(idx2)

            val q1peek = queue1.queue.peek()
            val q2peek = queue2.queue.peek()
            if (q1peek == null && q2peek == null) {
                return null
            }
            val q = if (q1peek == null) queue2
            else if (q2peek == null) queue1
            else if(comp.compare(q1peek,q2peek) > 0) queue1
            else queue2

            var el: Node? = null
            if (q.lock.tryLock()) {
                try {
                    if (!q.queue.isEmpty()) {
                        el = q.queue.poll()
                        size.decrementAndGet()
                    }
                } finally {
                    q.lock.unlock()
                }
                return el
            }
        }
    }

    fun isEmpty(): Boolean {
        return size.compareAndSet(0, 0)
    }
}

class LockablePriorityQueue<Node>(comp: Comparator<Node>) {
    val lock = ReentrantLock()
    val queue = PriorityQueue<Node>(comp)
}