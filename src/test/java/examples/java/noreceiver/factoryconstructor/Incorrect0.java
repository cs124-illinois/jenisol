package examples.java.noreceiver.factoryconstructor;

public final class Incorrect0 {
  private final int value;

  private Incorrect0(int setValue) {
    value = setValue;
  }

  public int getValue() {
    return value;
  }

  public static Incorrect0 value() {
    return new Incorrect0(1);
  }
}
