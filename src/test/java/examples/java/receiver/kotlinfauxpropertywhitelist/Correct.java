package examples.java.receiver.kotlinfauxpropertywhitelist;

import edu.illinois.cs.cs125.jenisol.core.KotlinMirrorOK;

public class Correct {
  private int counter = 0;

  @KotlinMirrorOK
  public int getCounter() {
    return counter++;
  }
}
