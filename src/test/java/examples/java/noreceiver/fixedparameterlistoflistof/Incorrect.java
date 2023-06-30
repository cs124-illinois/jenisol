package examples.java.noreceiver.fixedparameterlistoflistof;

import java.util.List;

public class Incorrect {
  public static int addOne(List<Integer> values) {
    assert values != null && !values.isEmpty();
    return values.get(0) + 2;
  }
}
