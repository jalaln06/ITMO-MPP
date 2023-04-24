/**
 * В теле класса решения разрешено использовать только переменные делегированные в класс RegularInt.
 * Нельзя volatile, нельзя другие типы, нельзя блокировки, нельзя лазить в глобальные переменные.
 *
 * @author : Naghiyev Jalal
 */
class Solution : MonotonicClock {
    private var c1 by RegularInt(0)
    private var c2 by RegularInt(0)
    private var c3 by RegularInt(0)

    private var cc1 by RegularInt(0)
    private var cc2 by RegularInt(0)
    private var cc3 by RegularInt(0)


    override fun write(time: Time) {

        cc1 = time.d1
        cc2 = time.d2
        cc3 = time.d3
        // write right-to-left
        c3 = cc3
        c2 = cc2
        c1 = cc1

    }

    override fun read(): Time {
        // read left-to-right
        val r1=c1
        val r2=c2
        val r3=c3
        val R1 = Time(r1, r2, r3)
        val rr3 = cc3
        val rr2 = cc2
        val rr1 = cc1
        val R2 = Time(rr1, rr2, rr3)
        if(R1==R2){
            return R1
        }else{
            if(rr1==r1){
                if(rr2==r2){
                    return Time(r1,r2,rr3)
                }else{
                    return Time(r1,rr2,0)
                }
            }else{
                return Time(rr1,0,0)
            }
        }
    }
}