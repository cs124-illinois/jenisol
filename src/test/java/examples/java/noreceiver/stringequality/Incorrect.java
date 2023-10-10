package examples.java.noreceiver.stringequality;

@SuppressWarnings({"checkstyle:StringLiteralEquality", "StringEquality"})
public class Incorrect {
  public static boolean isEmpty(String input) {
    return input == "";
  }
}
