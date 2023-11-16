package examples.java.noreceiver.readhelloworld;

import edu.illinois.cs.cs125.jenisol.core.InputOutput;
import java.io.IOException;
import java.util.Scanner;

public class Incorrect0 {
  public static void test() throws IOException {
    String greeter =
        new Scanner(InputOutput.filesystem.get().getPath("/testing.txt"))
            .useDelimiter("\\A")
            .next();
    System.out.println("Hello, there!");
  }
}
