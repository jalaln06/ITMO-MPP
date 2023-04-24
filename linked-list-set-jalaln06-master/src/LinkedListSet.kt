package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node<E>(prev = first, element = null, next = null)

    init {
        first.setNext(last)
    }

    private val head = atomic(first)

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        var cur = first
        while (true) {
            if (cur != first && cur != last && cur.element == element) {
                return false
            }
            if (cur == last) {
                cur = first
            }
            val next = cur.next
            val first = cur == first || cur.element < element
            val sec = next == last || cur.next!!.element > element
            if (first && sec) {
                val new = Node<E>(cur, element, next)
                if (cur.casNext(next, new)) {
                    return true
                } else {
                    cur = this.first
                }
            }
            cur = cur.next!!
        }
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {
        var cur = first
        while (true) {
            val next = cur.next
            if (next == last) {
                return false
            }
            if (next!!.element == element) {
                next.removed.compareAndSet(false, true)
            }
            if (next.removed.value == true) {
                if (cur.removed.value == false) {
                    if (cur.casNext(next, next.next)) {
                        return true
                    }else{
                        cur = first
                    }
                }
            }
            cur = cur.next!!
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var cur = first
        while (true) {
            if (cur == first) {
                cur = cur.next!!
                continue
            }
            if (cur == last) {
                return false
            }
            if (cur.element == element) {
                return true
            }
            val next = cur.next
            if (next == last) {
                return false
            }
            if (element > cur.element && element < cur.next!!.element) {
                return false
            }
            cur = cur.next!!
        }
    }
}

private class Removed(val content: Any?)
private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _prev = atomic(prev)
    val prev get() = _prev.value
    fun setPrev(value: Node<E>?) {
        _prev.value = value
    }

    fun casPrev(expected: Node<E>?, update: Node<E>?) =
        _prev.compareAndSet(expected, update)

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }

    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)

    val removed: AtomicBoolean = atomic(false)
}