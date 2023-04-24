import java.util.concurrent.atomic.*
import java.util.concurrent.locks.LockSupport.park
import java.util.concurrent.locks.LockSupport.unpark

class Solution(val env: Environment) : Lock<Solution.Node> {
    // todo: необходимые поля (val, используем AtomicReference)
    val tail : AtomicReference<Node> = AtomicReference(null)

    override fun lock(): Node {

        val my = Node() // сделали узел
        // todo: алгоритм
        my.locked.set(true)
        val pred = tail.getAndSet(my)
        if(pred!=null){
            pred.next.value=my
            while(my.locked.get()){
                env.park()
            }
        }
        return my // вернули узел
    }

    override fun unlock(node: Node) {
        if(node.next.value==null){
            if (tail.compareAndSet(node,null)){
                return
            }
            else{
                while(node.next.value==null){
                    continue
                }
            }
        }
        node.next.value.locked.set(false)
        env.unpark(node.next.value.thread)
    }

    class Node {
        val thread = Thread.currentThread() // запоминаем поток, которые создал узел
        // todo: необходимые поля (val, используем AtomicReference)
        val locked : AtomicReference<Boolean> = AtomicReference(false)
        val next : AtomicReference<Node> = AtomicReference(null)
    }
}
