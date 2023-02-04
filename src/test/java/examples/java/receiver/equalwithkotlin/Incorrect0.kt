package examples.java.receiver.equalwithkotlin

@Suppress("IntroduceWhenSubject", "EqualsOrHashCode", "EqualsWithHashCodeExist")
class Incorrect0(private val value: Int) {
    override fun equals(other: Any?) = when {
        other!!.javaClass == javaClass -> {
            other as Incorrect0
            other.value == value
        }
        else -> false
    }
}