import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Bank implementation.
 *
 * :TODO: This implementation has to be made thread-safe.
 *
 * @author :TODO: Naghiyev Jalal
 */
class BankImpl(n: Int) : Bank {
    private val accounts: Array<Account> = Array(n) { Account() }

    override val numberOfAccounts: Int
        get() = accounts.size

    /**
     * :TODO: This method has to be made thread-safe.
     */
    override fun getAmount(index: Int): Long {
        return accounts[index].amount
    }

    /**
     * :TODO: This method has to be made thread-safe.
     */
    override val totalAmount: Long
        get() {
            accounts.forEach { it.lock() }
            var sum : Long = accounts.sumOf { it.amount }
            accounts.forEach { it.unlock() }
            return sum
        }

    /**
     * :TODO: This method has to be made thread-safe.
     */
    override fun deposit(index: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[index]
        account.lock()
        check(!(amount > Bank.MAX_AMOUNT || account.amount + amount > Bank.MAX_AMOUNT)) {
            account.unlock()
            "Overflow" }
        account.amount += amount
        var amo = account.amount
        account.unlock()
        return amo
    }

    /**
     * :TODO: This method has to be made thread-safe.
     */
    override fun withdraw(index: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[index]
        account.lock()
        check(account.amount - amount >= 0) {
            account.unlock()
            "Underflow" }
        account.amount -= amount
        var amo = account.amount
        account.unlock()
        return amo
    }

    /**
     * :TODO: This method has to be made thread-safe.
     */
    override fun transfer(fromIndex: Int, toIndex: Int, amount: Long) {
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromIndex != toIndex) { "fromIndex == toIndex" }
        val from = accounts[fromIndex]
        val to = accounts[toIndex]
        var a : Boolean = true
        while(a){
            if(from.lock.tryLock()){
                if(to.lock.tryLock()){
                    check(amount <= from.amount) { a=false
                        from.unlock()
                        to.unlock()
                        "Underflow" }
                    check(!(amount > Bank.MAX_AMOUNT || to.amount + amount > Bank.MAX_AMOUNT)) { a=false
                        from.unlock()
                        to.unlock()
                        "Overflow" }
                    from.amount -= amount
                    to.amount += amount
                    to.unlock()
                    from.unlock()
                    a=false
                }else{
                    from.lock.unlock()
                }
            }
        }
    }

    /**
     * Private account data structure.
     */
    class Account {
        /**
         * Amount of funds in this account.
         */
        var amount: Long = 0
        var lock : ReentrantLock = ReentrantLock()
        fun lock(){
            lock.lock()
        }
        fun unlock(){
            lock.unlock()
        }
    }
}