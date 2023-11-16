package examples.java.noreceiver.readhelloworldclass;

import edu.illinois.cs.cs125.jenisol.core.EdgeType;
import edu.illinois.cs.cs125.jenisol.core.InputOutput;
import edu.illinois.cs.cs125.jenisol.core.ProvideFileSystem;
import edu.illinois.cs.cs125.jenisol.core.RandomType;
import edu.illinois.cs.cs125.jenisol.core.SimpleType;
import edu.illinois.cs.cs125.jenisol.core.generators.JenisolFileSystem;
import java.io.IOException;
import java.util.Random;
import java.util.Scanner;

@ProvideFileSystem
public class Correct {
  String value;

  public String first() throws IOException {
    return new Scanner(InputOutput.filesystem.get().getPath("/testing.txt"))
        .useDelimiter("\\A")
        .next();
  }

  public String second() throws IOException {
    return new Scanner(InputOutput.filesystem.get().getPath("/testing.txt"))
        .useDelimiter("\\A")
        .next();
  }

  @SimpleType
  private static final JenisolFileSystem[] SIMPLE =
      new JenisolFileSystem[] {new JenisolFileSystem("/testing.txt", "Hello, world!")};

  @EdgeType private static final JenisolFileSystem[] EDGE = new JenisolFileSystem[] {};

  @RandomType
  private static JenisolFileSystem randomInput(Random random) {
    return new JenisolFileSystem("/testing.txt", ("" + random.nextInt()));
  }
}
