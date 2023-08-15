package examples.java.noreceiver.returnsstream;

import java.util.stream.Stream;

public class Incorrect0 {
  public static Stream<Integer> value() {
    return Stream.of(1, 2, 4, 8);
  }
}
