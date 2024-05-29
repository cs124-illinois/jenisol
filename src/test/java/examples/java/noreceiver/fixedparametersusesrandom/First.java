package examples.java.noreceiver.fixedparametersusesrandom;

import edu.illinois.cs.cs125.jenisol.core.FixedParameters;
import edu.illinois.cs.cs125.jenisol.core.Limit;
import java.util.List;
import java.util.Random;

public class First {
  private static final Random RANDOM = new Random(124);

  @Limit(1)
  public static int addOne(int value) {
    return value + 1;
  }

  @FixedParameters private static final List<Integer> FIXED = List.of(RANDOM.nextInt());
}
