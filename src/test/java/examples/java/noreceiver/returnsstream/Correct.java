package examples.java.noreceiver.returnsstream;

import java.util.stream.Stream;

public class Correct {
  public static Stream<Integer> value() {
    return Stream.of(1, 2, 4);
  }
}
