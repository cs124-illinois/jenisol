package examples.java.receiver.equalwithkotlin

@Suppress("EqualsOrHashCode", "EqualsWithHashCodeExist")
class Correct0(private val value: Int) {
    override fun equals(other: Any?): Boolean {
        if (other !is Correct0) {
            return false
        }
        return value == other.value
    }
}
