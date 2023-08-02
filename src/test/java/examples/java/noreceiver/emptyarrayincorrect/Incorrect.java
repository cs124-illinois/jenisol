package examples.java.noreceiver.emptyarrayincorrect;

public class Incorrect {
  int arraySize(double[] values) {
    if (values.length == 0) {
      return -1;
    }
    return values.length;
  }
}
