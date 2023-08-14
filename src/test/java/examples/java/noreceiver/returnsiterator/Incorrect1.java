package examples.java.noreceiver.returnsiterator;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class Incorrect1 {
  public static Iterator<Integer> value() {
    return new Iterator<>() {
      private int index = 0;
      private List<Integer> values = Arrays.asList(1, 2, 4);

      @Override
      public boolean hasNext() {
        return true;
      }

      @Override
      public Integer next() {
        int toReturn = values.get(index);
        index = (index + 1) % values.size();
        return toReturn;
      }
    };
  }
}
