package examples.java.receiver.firstequalssecond;

import edu.illinois.cs.cs125.jenisol.core.Initializer;
import edu.illinois.cs.cs125.jenisol.core.NotNull;

public class SimpleArrayList {
  protected Object[] values;

  @Initializer
  private void setValues(@NotNull Object[] setValues) {
    values = setValues;
  }
}
