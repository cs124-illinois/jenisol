package examples.java.noreceiver.parameterdisambiguation;

import edu.illinois.cs.cs125.jenisol.core.FixedParameters;
import edu.illinois.cs.cs125.jenisol.core.NotNull;
import edu.illinois.cs.cs125.jenisol.core.RandomParameters;
import edu.illinois.cs.cs125.jenisol.core.SkipTest;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@SuppressWarnings("StringConcatenationInLoop")
public class Correct {
  public static int length(String input) {
    return input.length();
  }

  public static String reversed(@NotNull String input) {
    if (input.equals("a".repeat(input.length()))) {
      throw new SkipTest();
    }
    String result = "";
    for (int i = 0; i < input.length(); i++) {
      result += input.charAt(input.length() - i - 1);
    }
    return result;
  }

  @FixedParameters("length")
  private static final List<String> FIXED = Collections.singletonList("");

  @RandomParameters("length")
  private static String randomString(Random random) {
    return "a".repeat(random.nextInt(32) + 1);
  }
}
