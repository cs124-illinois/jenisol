package examples.java.receiver.necessaryinnerclass;

public class Incorrect0 {
  private final Value value;

  public Incorrect0(int setValue) {
    value = new Value(setValue);
  }

  public int plusOne() {
    return value.value + 2;
  }

  public class Value {
    private final int value;

    public Value(int setValue) {
      value = setValue;
    }
  }
}
