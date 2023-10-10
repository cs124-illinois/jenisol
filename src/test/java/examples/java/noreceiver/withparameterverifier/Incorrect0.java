package examples.java.noreceiver.withparameterverifier;

@SuppressWarnings({"unused", "ParameterCanBeLocal"})
public class Incorrect0 {
  @SuppressWarnings("checkstyle:ParameterAssignment")
  public static int both(int[] values) {
    values = new int[] {0, 1};
    return values.length;
  }
}
