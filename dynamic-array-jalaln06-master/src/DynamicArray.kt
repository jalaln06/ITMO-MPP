package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.lang.IllegalArgumentException

interface DynamicArray<E> {
    /**
     * Returns the element located in the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the [size] of this array.
     */
    fun get(index: Int): E

    /**
     * Puts the specified [element] into the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the [size] of this array.
     */
    fun put(index: Int, element: E)

    /**
     * Adds the specified [element] to this array
     * increasing its [size].
     */
    fun pushBack(element: E)

    /**
     * Returns the current size of this array,
     * it increases with [pushBack] invocations.
     */
    val size: Int
}

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core(INITIAL_CAPACITY))
    val _size = atomic(0)

    override fun get(index: Int): E {
        if (index >= _size.value) {
            throw IllegalArgumentException()
        }
        var core = this.core.value
        while (true) {
            when (val value = core.array[index].value) {
                is Transferring -> return value.content as E
                is Final -> {
                    core = core.next.value!!
                    continue
                }

                else -> return value as E
            }
        }
    }

    override fun put(index: Int, element: E) {
        if (index >= _size.value) {
            throw IllegalArgumentException()
        }
        var core = this.core.value
        while (true) {
            when (val value = core.array[index].value) {
                is Transferring -> {

                    core.next.value!!.array[index].compareAndSet(null, value.content)
                    core.array[index].compareAndSet(value, Final(value.content))
                    if (core.next.value!!.array[index].compareAndSet(value.content, element)) {
                        return
                    }
                }

                is Final -> {
                    core = core.next.value!!
                    continue
                }

                else -> {
                    if (core.array[index].compareAndSet(value, element)) {
                        return
                    }
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val core = core.value
            val size = _size.value
            if (size >= core.capacity) {
                val next = moveCore(core)
                next ?: continue
                this.core.compareAndSet(core,next)
                continue
            }
            if (core.array[size].compareAndSet(null, element)) {
                _size.compareAndSet(size, size + 1)
                return
            } else {
                _size.compareAndSet(size, size + 1)
            }
        }
    }

    private fun moveCore(core: Core) : Core? {
        if (core.next.value == null) {
            core.next.compareAndSet(null, Core(2 * core.capacity))
        }

        val next = core.next.value ?: return null

        for (i in 0 until core.capacity) {
            while (true) {
                when (val el = core.array[i].value) {
                    is Transferring -> {
                        next.array[i].compareAndSet(null, el.content)
                        core.array[i].compareAndSet(el, Final(el.content))
                        break
                    }

                    is Final -> break
                    else -> {
                        if (core.array[i].compareAndSet(el, Transferring(el))) {
                            continue
                        }
                    }
                }

            }
        }

        return next
    }

    override val size: Int get() = _size.value

    private class Core(
        val capacity: Int,
    ) {
        val array = atomicArrayOfNulls<Any>(capacity)
        val next: AtomicRef<Core?> = atomic(null)
    }

    private class Transferring(val content: Any?)
    private class Final(val content: Any?)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME