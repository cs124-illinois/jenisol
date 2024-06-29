package examples.java.noreceiver.randomparameternocomplexity;

import edu.illinois.cs.cs125.jenisol.core.FixedParameters;
import edu.illinois.cs.cs125.jenisol.core.One;
import edu.illinois.cs.cs125.jenisol.core.RandomParameters;
import java.util.List;
import java.util.Random;

public class Correct {
  @FixedParameters private static final List<One<Long>> FIXED = List.of(new One<>(8888L));

  @RandomParameters
  private static Long valueParameters(Random random) {
    return random.nextLong();
  }

  public static boolean value(long first) {
    return first == 8888;
  }
}
