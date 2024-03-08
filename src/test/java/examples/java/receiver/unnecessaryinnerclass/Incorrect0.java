package examples.java.receiver.unnecessaryinnerclass;

public class Incorrect0 {
  private final int value;

  public Incorrect0(int setValue) {
    value = setValue;
  }

  public int plusOne() {
    return value + 2;
  }
}
