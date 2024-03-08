package examples.java.receiver.unnecessaryinnerclass;

public class Correct {
  private final int value;

  public Correct(int setValue) {
    value = setValue;
  }

  public int plusOne() {
    return value + 1;
  }
}
