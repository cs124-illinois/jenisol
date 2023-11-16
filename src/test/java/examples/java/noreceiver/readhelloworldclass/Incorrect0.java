package examples.java.noreceiver.readhelloworldclass;

import edu.illinois.cs.cs125.jenisol.core.InputOutput;
import edu.illinois.cs.cs125.jenisol.core.ProvideFileSystem;
import java.io.IOException;
import java.util.Scanner;

@ProvideFileSystem
public class Incorrect0 {
  String value;

  public String first() throws IOException {
    String loadedValue =
        new Scanner(InputOutput.filesystem.get().getPath("/testing.txt"))
            .useDelimiter("\\A")
            .next();
    return loadedValue + loadedValue;
  }

  public String second() throws IOException {
    return new Scanner(InputOutput.filesystem.get().getPath("/testing.txt"))
        .useDelimiter("\\A")
        .next();
  }
}
