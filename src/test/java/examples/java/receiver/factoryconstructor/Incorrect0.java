package examples.java.receiver.factoryconstructor;

public final class Incorrect0 {
  private int value;

  private Incorrect0(int setValue) {
    value = setValue;
  }

  public int getValue() {
    return 0;
  }

  public static Incorrect0 create(int setValue) {
    return new Incorrect0(setValue);
  }
}
