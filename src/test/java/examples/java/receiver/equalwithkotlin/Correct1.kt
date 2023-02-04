package examples.java.receiver.equalwithkotlin

@Suppress("IntroduceWhenSubject", "EqualsOrHashCode", "EqualsWithHashCodeExist")
class Correct1(private val value: Int) {
    override fun equals(other: Any?) = when {
        other?.javaClass == javaClass -> {
            other as Correct1
            other.value == value
        }
        else -> false
    }
}