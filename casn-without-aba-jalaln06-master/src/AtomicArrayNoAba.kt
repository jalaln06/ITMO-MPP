import kotlinx.atomicfu.*
import kotlin.math.exp

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a: Array<Ref<E>> = Array(size) { Ref(initialValue) }

    class Ref<E : Any>(initial: E) {
        val v : AtomicRef<Any> = atomic(initial)

        fun get(): E {
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> {
                        return cur as E
                    }
                }
            }
        }

        fun set(upd: E) {
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd))
                        return
                }
            }
        }
        fun CAS(expect: Any?, update: Any) : Boolean{
            v.loop { cur ->
                when(cur) {
                    expect -> if (v.compareAndSet(expect,update)) return true
                    is Descriptor -> cur.complete()
                    else -> return false
                } }
        }
    }

    class CASNDescriptor<E : Any>(
        val a: Ref<E>, val expectA: E, val updateA: E,
        val b: Ref<E>, val expectB: E, val updateB: E
    ) : Descriptor() {
        val outcome: AtomicRef<OUTCOME> = atomic(OUTCOME.UNDECIDED)
        override fun complete(): Boolean {
            if(b.v.value!=this){
                var desc = RDCSSDescriptor(b, expectB, this)
                if(b.CAS(expectB,desc)){
                    desc.complete()
                }
            }
//           val bval = b.v.value
//            if(bval is RDCSSDescriptor<*>){
//                if(bval.a.v.value==b.v.value){
//                    desc= bval as RDCSSDescriptor<E>
//                }else{
//                    return false
//                }
//            }else if(bval==this){
//
//            }else  {
//                b.CAS(expectB, desc)
//            }

            if (b.v.value==this) {
                outcome.compareAndSet(OUTCOME.UNDECIDED, OUTCOME.SUCCESS)
            } else {
                outcome.compareAndSet(OUTCOME.UNDECIDED, OUTCOME.FAILED)
            }
            if (outcome.value == OUTCOME.SUCCESS) {
                a.v.compareAndSet(this, updateA)
                b.v.compareAndSet(this, updateB)
                return true
            }
            if (outcome.value == OUTCOME.FAILED) {
                a.v.compareAndSet(this,expectA)
                return false
            }
            return false
        }
    }


    class RDCSSDescriptor<E : Any>(
        val a: Ref<E>, val expectA: E, val updateA: CASNDescriptor<*>
    ) : Descriptor() {
        val outcome: AtomicRef<Any?> = atomic(OUTCOME.UNDECIDED)

        override fun complete(): Boolean {
            if (updateA.outcome.value == OUTCOME.UNDECIDED) {
                outcome.compareAndSet(OUTCOME.UNDECIDED, OUTCOME.SUCCESS)
            } else {
                outcome.compareAndSet(OUTCOME.UNDECIDED, OUTCOME.FAILED)
            }
            if (outcome.value == OUTCOME.FAILED) {
                a.v.compareAndSet(this, expectA)
                return false
            }
            if (outcome.value == OUTCOME.SUCCESS) {
                a.v.compareAndSet(this, updateA)
                return true
            }
            return false
        }
    }

    abstract class Descriptor {
        abstract fun complete(): Boolean
    }

    enum class OUTCOME {
        UNDECIDED, FAILED, SUCCESS
    }

    fun get(index: Int) =
        a[index].get() as Int

    fun cas(index: Int, expected: E, update: E) =
        a[index].v.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (index1 == index2) {
            return cas(index1, expected1, ((expected1 as Int) + 2)as E)
        }
        var desc1 : Any?
        if(index1>index2){
            desc1 = CASNDescriptor(a[index2], expected2, update2, a[index1], expected1, update1)
            if (a[index2].CAS(expected2, desc1)) {
                return (desc1 as CASNDescriptor<*>).complete()
            } else {
                return false
            }
        }else{
            desc1 = CASNDescriptor(a[index1], expected1, update1, a[index2], expected2, update2)
            if (a[index1].CAS(expected1, desc1)) {
                return (desc1 as CASNDescriptor<*>).complete()
            } else {
                return false
            }
        }

    }
}