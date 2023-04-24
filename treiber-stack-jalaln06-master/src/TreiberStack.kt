package mpp.stack

import kotlinx.atomicfu.atomic

class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        do {
          val oldTop = top.value
        } while (!top.compareAndSet(oldTop, Node(x,oldTop)))
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var oldTop = top.value
        do {
            oldTop = top.value
            oldTop ?: return null
        } while (!top.compareAndSet(oldTop, oldTop!!.next))
        return oldTop.x
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT