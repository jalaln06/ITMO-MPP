package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

class MSQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: E) {
        var node = Node(x)
        while (true){
            var tail = this.tail.value
            if(tail.next.compareAndSet(null,node)){
                this.tail.compareAndSet(tail,node)
                return
            }
            else{
                this.tail.compareAndSet(tail, tail.next.value!!)
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while (true){
            val head = this.head.value
            val next = head.next.value
            if(next==null){
                return null;
            }
            val result = next.x
            if(this.head.compareAndSet(head,next)){
                return result
            }
        }
    }

    fun isEmpty(): Boolean {
        val head = this.head.value
        return head.next.value==null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}