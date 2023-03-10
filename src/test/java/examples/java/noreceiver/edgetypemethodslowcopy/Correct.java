package examples.java.noreceiver.edgetypemethodslowcopy;

import edu.illinois.cs.cs125.jenisol.core.EdgeType;
import edu.illinois.cs.cs125.jenisol.core.RandomType;
import edu.illinois.cs.cs125.jenisol.core.SimpleType;
import java.util.Random;

public class Correct {
  public static int get(Blob blob) {
    return blob.getValue();
  }

  @SimpleType private static final Blob[] SIMPLE = new Blob[] {};

  @EdgeType(fastCopy = false)
  private static Blob[] edgeBlob() {
    return new Blob[] {};
  }

  @RandomType(fastCopy = false)
  private static Blob randomBlob(int complexity, Random random) {
    return new Blob(random.nextInt());
  }
}
