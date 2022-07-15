package examples.java.receiver.receiverparameter;

public class Correct {
  private int value;

  public Correct() {
    value = 0;
  }

  public Correct(int setValue) {
    value = setValue;
  }

  public int getValue() {
    return value;
  }

  public int inc(Correct other) {
    return other.value++;
  }
}
