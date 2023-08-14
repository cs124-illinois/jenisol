package examples.java.noreceiver.returnsiterator;

import java.util.Arrays;
import java.util.Iterator;

public class Correct {
  public static Iterator<Integer> value() {
    return Arrays.asList(1, 2, 4).iterator();
  }
}
