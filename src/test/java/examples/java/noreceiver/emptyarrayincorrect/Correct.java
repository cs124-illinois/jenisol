package examples.java.noreceiver.emptyarrayincorrect;

import edu.illinois.cs.cs125.jenisol.core.FixedParameters;
import edu.illinois.cs.cs125.jenisol.core.NotNull;
import edu.illinois.cs.cs125.jenisol.core.RandomParameters;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Correct {
  int arraySize(@NotNull double[] values) {
    return values.length;
  }

  @FixedParameters
  private static final List<double[]> FIXED =
      Arrays.asList(new double[0], new double[] {1.0, 2.0, 3.0});

  @RandomParameters
  private static double[] randomParameters(Random random) {
    return new double[random.nextInt(32) + 8];
  }
}
