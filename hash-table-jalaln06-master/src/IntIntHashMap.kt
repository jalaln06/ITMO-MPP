import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.math.abs

/**
 * Int-to-Int hash map with open addressing and linear probes.
 *
 * TODO: This class is **NOT** thread-safe.
 */
class IntIntHashMap {
    private val core = atomic(Core(INITIAL_CAPACITY))

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(core.value.getInternal(key))
    }

    /**
     * Changes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key   a positive key.
     * @param value a positive value.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key or value are not positive, or value is equal to
     * [Integer.MAX_VALUE] which is reserved.
     */
    fun put(key: Int, value: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        require(isValue(value)) { "Invalid value: $value" }
        return toValue(putAndRehashWhileNeeded(key, value))
    }

    /**
     * Removes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key a positive key.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    fun remove(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(putAndRehashWhileNeeded(key, DEL_VALUE))
    }

    private fun putAndRehashWhileNeeded(key: Int, value: Int): Int {
        while (true) {
            val curCore=core.value
            val oldValue = curCore.putInternal(key, value)
            if (oldValue != NEEDS_REHASH) return oldValue
            core.compareAndSet(curCore,curCore.rehash())
        }
    }

    private class Core internal constructor(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val map = AtomicIntArray(2 * capacity)
        val shift: Int
        val next: AtomicRef<Core?> = atomic(null)

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (map[index].value != key) { // optimize for successful lookup
               // if(map[index].value<0||map[index].value==FINAL) return next.value!!.getInternal(key)
                if (map[index].value == NULL_KEY) return NULL_VALUE // not found -- no value
                if (++probes >= MAX_PROBES) return NULL_VALUE
                if (index == 0) index = map.size
                index -= 2
            }
            // found key -- return value
            return when (val value = map[index + 1].value) {
                DEL_VALUE -> NULL_VALUE
                NULL_VALUE -> NULL_VALUE
                FINAL -> this.next.value!!.getInternal(key)
                else -> abs(value)
            }
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0
            while (true) { // optimize for successful lookup
                val value1 = map[index].value
                if(value1 == key){
                    break
                }
                if (value1 == NULL_KEY) {
                    // not found -- claim this slot
                    if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                    if (!map[index].compareAndSet(NULL_KEY, key)) {
                        continue
                    } else {
                        break
                    }
                }
                if (++probes >= MAX_PROBES) return NEEDS_REHASH
                if (index == 0) index = map.size
                index -= 2
            }
            // found key -- update value
            while (true) {
                val oldValue = map[index + 1].value
                when {
                    oldValue == FINAL -> return next.value!!.putInternal(key, value)
                    oldValue < 0 -> return NEEDS_REHASH//MOVING
                    else -> if (map[index + 1].compareAndSet(oldValue, value)) {
                        return if (oldValue == DEL_VALUE) NULL_VALUE else return abs(oldValue)
                    } else {
                        continue
                    }
                }
            }
        }
        fun putInternalInCore(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0
            while (true) { // optimize for successful lookup
                val value1 = map[index].value
                if(value1 == key){
                    break
                }
                if (value1 == NULL_KEY) {
                    // not found -- claim this slot
                    if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                    if (!map[index].compareAndSet(NULL_KEY, key)) {
                        continue
                    } else {
                        break
                    }
                }
                if (++probes >= MAX_PROBES) return NEEDS_REHASH
                if (index == 0) index = map.size
                index -= 2
            }
            map[index + 1].compareAndSet(NULL_VALUE, value)
            return 3456789
//            // found key -- update value
//            while (true) {
//                val oldValue = map[index + 1].value
//                when {
//                    oldValue == FINAL -> return next.value!!.putInternalInCore(key, value)
//                    oldValue < 0 -> return NEEDS_REHASH//MOVING
//                    else ->{
//                        map[index + 1].compareAndSet(NULL_VALUE, value)
//                        return oldValue
//                    }
//                }
//            }
        }

        fun rehash(): Core { // map.length is twice the current capacity
            if (this.next.value == null) {
                this.next.compareAndSet(null, Core(map.size))
            }
            val newCore =  this.next.value!!
            var index = 0
            while (index < map.size) {
                val value = map[index+1].value
                if (isValue(map[index + 1].value)) {
                    map[index+1].compareAndSet(value,-1*value)
                }
                if(value==FINAL){
                    index+=2
                    continue
                }
                if(value<0){//MOVED
                    newCore.putInternalInCore(map[index].value, -1*value)

                    map[index+1].compareAndSet(value,FINAL)
                }
                if(value==DEL_VALUE || value == NULL_VALUE){
                    map[index+1].compareAndSet(value,FINAL)
                }
            }
            return next.value!!
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        fun index(key: Int): Int = (key * MAGIC ushr shift) * 2
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
private const val MAX_PROBES = 8 // max number of probes to find an item
private const val NULL_KEY = 0 // missing key (initial value)
private const val NULL_VALUE = 0 // missing value (initial value)
private const val FINAL = Int.MIN_VALUE //
private const val DEL_VALUE = Int.MAX_VALUE// mark for removed value
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0