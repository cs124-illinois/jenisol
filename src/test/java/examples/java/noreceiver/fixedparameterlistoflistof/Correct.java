package examples.java.noreceiver.fixedparameterlistoflistof;

import edu.illinois.cs.cs125.jenisol.core.FixedParameters;
import java.util.List;

public class Correct {
  public static int addOne(List<Integer> values) {
    assert values != null && !values.isEmpty();
    return values.get(0) + 1;
  }

  @FixedParameters private static final List<List<Integer>> FIXED = List.of(List.of(1));
}
