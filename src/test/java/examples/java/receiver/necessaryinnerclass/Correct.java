package examples.java.receiver.necessaryinnerclass;

public class Correct {
  private final Value value;

  public Correct(int setValue) {
    value = new Value(setValue);
  }

  public int plusOne() {
    return value.value + 1;
  }

  public class Value {
    private final int value;

    public Value(int setValue) {
      value = setValue;
    }
  }
}
