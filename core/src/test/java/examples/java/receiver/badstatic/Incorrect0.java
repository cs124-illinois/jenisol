package examples.java.receiver.badstatic;

public class Incorrect0 {
  private static int counter = 0;

  public int increment() {
    return counter++;
  }
}
