package examples.java.noreceiver.fixedparameterlistof;

import edu.illinois.cs.cs125.jenisol.core.FixedParameters;
import java.util.List;

public class Correct {
  public static int addOne(int first) {
    return first + 1;
  }

  @FixedParameters private static final List<Integer> FIXED = List.of(1);
}
