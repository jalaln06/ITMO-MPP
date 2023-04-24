import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * An element is transferred from sender to receiver only when [send] and [receive]
 * invocations meet in time (rendezvous), so [send] suspends until another coroutine
 * invokes [receive] and [receive] suspends until another coroutine invokes [send].
 */
class SynchronousQueue<E> {
    private val queue: MSQueue<E> = MSQueue<E>()

    private val dummy : E? = null
    /**
     * Sends the specified [element] to this channel, suspending if there is no waiting
     * [receive] invocation on this channel.
     */
    suspend fun send(element: E): Unit {
        val offer = Node(element, Type.SEND)
        while (true) {
            val h = queue.head.value
            val t = queue.tail.value
            if (h == t || t.getTypeNotJVM() == Type.SEND) {
                val next = t.next.value
                if (t == queue.tail.value) {
                    if (next != null) {
                        queue.tail.compareAndSet(t, next)
                    } else {
                        val res = suspendCoroutine<Boolean> sc@ {continuation ->
                            offer.con = continuation
                            if(t.next.compareAndSet(next, offer)){
                                queue.tail.compareAndSet(t, offer)
                            }else{
                                continuation.resume(false)
                            }
                        }
                        if (res) {
                            val he = queue.head.value
                            if (offer == he.next.value) {
                                queue.head.compareAndSet(he, offer)
                            }
                            return
                        }
                    }
                }
            } else {
                val next = h.next.value
                if (t != queue.tail.value || h != queue.head.value || next == null) {
                    continue
                }
                val suc = next.x.compareAndSet(null, element)
                queue.head.compareAndSet(h, next)
                if (suc) {
                    next.con!!.resume(true)
                    return
                }
            }
        }
    }

    /**
     * Retrieves and removes an element from this channel if there is a waiting [send] invocation on it,
     * suspends the caller if this channel is empty.
     */


    suspend fun receive(): E {
        val offer = Node(dummy, Type.RECEIVE)
        while (true) {
            val h = queue.head.value
            val t = queue.tail.value
            if (h == t || t.getTypeNotJVM() == Type.RECEIVE) {
                val next = t.next.value
                if (t == queue.tail.value) {
                    if (next != null) {
                        queue.tail.compareAndSet(t, next)
                    } else {
                        val res = suspendCoroutine<Boolean> sc@ {continuation ->
                            offer.con = continuation
                            if(t.next.compareAndSet(next, offer)){
                                queue.tail.compareAndSet(t, offer)
                            }else{
                                continuation.resume(false)
                            }
                        }
                        if (res) {
                            val he = queue.head.value
                            if (offer == he.next.value) {
                                queue.head.compareAndSet(he, offer)
                            }
                            return offer.x.value!!
                        }
                    }
                }
            } else {
                val next = h.next.value
                if (t != queue.tail.value || h != queue.head.value || next == null) {
                    continue
                }
                val element = next.x.value
                val con = next.con
                val suc = next.x.compareAndSet(element, null)
                queue.head.compareAndSet(h, next)
                if(element==null){
                    continue
                }
                if (suc) {
                    con!!.resume(true)
                    return element!!
                }
            }
        }
    }
}

class MSQueue<E> {
    public val head: AtomicRef<Node<E>>
    public val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null,Type.RECEIVE)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    fun isEmpty(): Boolean {
        val head = this.head.value
        return head.next.value == null
    }
}

class Node<E>(x: E?, val type: Type) {
    val x: AtomicRef<E?> = atomic(x)
    var con : Continuation<Boolean>? = null
    val next = atomic<Node<E>?>(null)
    fun getTypeNotJVM(): Type = type
}

enum class Type {
    SEND, RECEIVE
}
