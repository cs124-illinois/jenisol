package examples.java.designonly;

import edu.illinois.cs.cs125.jenisol.core.DesignOnly;

@DesignOnly
public class Correct {
  @SuppressWarnings("checkstyle:VisibilityModifier")
  public int value;
}
