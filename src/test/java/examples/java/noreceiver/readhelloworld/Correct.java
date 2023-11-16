package examples.java.noreceiver.readhelloworld;

import edu.illinois.cs.cs125.jenisol.core.EdgeType;
import edu.illinois.cs.cs125.jenisol.core.InputOutput;
import edu.illinois.cs.cs125.jenisol.core.ProvideFileSystem;
import edu.illinois.cs.cs125.jenisol.core.RandomType;
import edu.illinois.cs.cs125.jenisol.core.SimpleType;
import edu.illinois.cs.cs125.jenisol.core.generators.JenisolFileSystem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

public class Correct {
  @ProvideFileSystem
  public static void test() throws IOException {
    String greeter =
        new Scanner(InputOutput.filesystem.get().getPath("/testing.txt"))
            .useDelimiter("\\A")
            .next();
    System.out.println(greeter);
  }

  @SimpleType
  private static final JenisolFileSystem[] SIMPLE =
      new JenisolFileSystem[] {
        new JenisolFileSystem(
            Map.of("/testing.txt", "Hello, world!".getBytes(StandardCharsets.UTF_8)))
      };

  @EdgeType private static final JenisolFileSystem[] EDGE = new JenisolFileSystem[] {};

  @RandomType
  private static JenisolFileSystem randomInput(Random random) {
    return new JenisolFileSystem(
        Map.of("/testing.txt", ("" + random.nextInt()).getBytes(StandardCharsets.UTF_8)));
  }
}
