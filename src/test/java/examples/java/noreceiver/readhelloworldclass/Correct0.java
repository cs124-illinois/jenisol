package examples.java.noreceiver.readhelloworldclass;

import edu.illinois.cs.cs125.jenisol.core.InputOutput;
import edu.illinois.cs.cs125.jenisol.core.ProvideFileSystem;
import java.io.IOException;
import java.util.Scanner;

@ProvideFileSystem
public class Correct0 {
  String value;

  public String first() throws IOException {
    if (value == null) {
      value =
          new Scanner(InputOutput.filesystem.get().getPath("/testing.txt"))
              .useDelimiter("\\A")
              .next();
    }
    return value;
  }

  public String second() throws IOException {
    if (value == null) {
      value =
          new Scanner(InputOutput.filesystem.get().getPath("/testing.txt"))
              .useDelimiter("\\A")
              .next();
    }
    return value;
  }
}
