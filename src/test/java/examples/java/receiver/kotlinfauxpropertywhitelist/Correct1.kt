package examples.java.receiver.kotlinfauxpropertywhitelist

class Correct1 {
    private var counter = 0
    fun getCounter(): Int {
        return counter++
    }
}