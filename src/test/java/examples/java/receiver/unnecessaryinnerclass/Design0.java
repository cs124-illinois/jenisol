package examples.java.receiver.unnecessaryinnerclass;

public class Design0 {
  private final int value;

  public Design0(int setValue) {
    value = setValue;
  }

  public int plusOne() {
    return value + 1;
  }

  public static class Unnecessary {}
}
