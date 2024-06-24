package examples.java.noreceiver.stringsplit

class Incorrect1 private constructor() {
    companion object {
        fun part(string: String): String = string.split(" ")[2]
    }
}
