package examples.java.noreceiver.multiplemethodssharedparameters;

@SuppressWarnings("unused")
public class Incorrect0 {
  public static String processName(String name) {
    return "Wrong: " + name;
  }

  public static String validateName(String name) {
    return "Invalid";
  }

  public static String checkName(String name) {
    return "WRONG";
  }

  public static String formatName(String name) {
    return "bad";
  }

  public static String sanitizeName(String name) {
    return "WRONG_SANITIZED";
  }
}
