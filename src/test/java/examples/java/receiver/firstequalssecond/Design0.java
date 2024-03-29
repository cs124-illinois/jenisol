package examples.java.receiver.firstequalssecond;

public class Design0 extends SimpleArrayList {
  public boolean firstEqualsSecond(SimpleArrayList list) {
    if (list.values.length <= 1) {
      throw new IllegalArgumentException();
    }
    return list.values[0].equals(values[1]);
  }
}
