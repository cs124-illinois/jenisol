package examples.java.noreceiver.multiplemethodssharedparameters;

import edu.illinois.cs.cs125.jenisol.core.RandomParameters;
import java.util.Random;

public class Correct {
  // Four methods that all take a String parameter
  public static String processName(String name) {
    return "Processed: " + name;
  }

  public static String validateName(String name) {
    if (name != null && !name.isEmpty()) {
      return "Valid";
    }
    return "Invalid";
  }

  public static String checkName(String name) {
    if (name != null) {
      return name.toUpperCase();
    }
    return "NULL";
  }

  public static String formatName(String name) {
    if (name != null) {
      return name.trim();
    }
    return "";
  }

  // This one needs special parameter generation (e.g., must include certain edge cases)
  public static String sanitizeName(String name) {
    if (name == null) {
      return "SANITIZED_NULL";
    }
    // Special handling for strings that might contain SQL injection attempts
    if (name.contains("DROP") || name.contains("DELETE")) {
      return "SANITIZED_UNSAFE";
    }
    return name.replaceAll("[^a-zA-Z0-9 ]", "");
  }

  // Special generator for sanitizeName - includes SQL-like strings
  @RandomParameters("sanitizeName")
  private static String sanitizeNameParameters(Random random) {
    String[] specialCases = {
      null,
      "DROP TABLE users",
      "DELETE FROM data",
      "Robert'); DROP TABLE students;--",
      "normal name",
      "name with symbols!@#",
      ""
    };
    return specialCases[random.nextInt(specialCases.length)];
  }

  // Default generator for the other four methods
  @RandomParameters("*")
  private static String defaultNameParameters(Random random) {
    String[] normalNames = {
      "Alice", "Bob", "Charlie", null, "", "  spaces  ", "lowercase", "UPPERCASE"
    };
    return normalNames[random.nextInt(normalNames.length)];
  }
}
