package examples.java.receiver.kotlinfauxpropertywhitelist

@Suppress("UselessPostfixExpression")
class Correct0 {
    var counter = 0
        private set
        get() {
            return field++
        }
}