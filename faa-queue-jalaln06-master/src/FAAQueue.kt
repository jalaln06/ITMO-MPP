package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var s = start
        while (true){
            if(s.id==id){
                return s
            }
            if (s.next.value==null){
                val d = Segment(s.id+1)
                if(!s.next.compareAndSet(null,d)){
                    continue
                }
            }
            s = s.next.value!!
        }

    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val cur_tail = tail.value
            val i = enqIdx.getAndAdd(+1)
            val s = findSegment(start = cur_tail, id = i / SEGMENT_SIZE)
            if(s.id>cur_tail.id){
                tail.compareAndSet(cur_tail,s)
            }
            if (s.cas((i % SEGMENT_SIZE).toInt(),null,element)){
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value){
                return null}
            val cur_head = head.value
            val i = deqIdx.getAndAdd(+1)
            val s = findSegment(start = cur_head, id = i / SEGMENT_SIZE)
            if(s.id>cur_head.id){
                head.compareAndSet(cur_head,s)
            }
            if (s.cas((i % SEGMENT_SIZE).toInt(),null, MAX_INT)){
                continue
            }
            val a = s.get((i% SEGMENT_SIZE).toInt()) as E?
            return a
        }
    }
    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            TODO("implement me")
        }
}

private class Segment(val id: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    public fun get(i: Int) = elements[i].value
    public fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    public fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}
const val MAX_INT = Integer.MAX_VALUE
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

