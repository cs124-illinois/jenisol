package examples.java.receiver.firstequalssecond;

public class Incorrect0 extends SimpleArrayList {
  public boolean firstEqualsSecond() {
    if (values.length <= 1) {
      throw new IllegalArgumentException();
    }
    return values[0].equals(values[0]);
  }
}
