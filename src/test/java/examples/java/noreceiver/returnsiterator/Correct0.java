package examples.java.noreceiver.returnsiterator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Correct0 {
  public static Iterator<Integer> value() {
    Map<Integer, Boolean> values = new HashMap<>();
    values.put(1, true);
    values.put(2, false);
    values.put(4, true);
    return values.keySet().iterator();
  }
}
