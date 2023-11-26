package examples.java.receiver.moveablelocation;

public interface IMovableLocation {
  // Shift the location north by the specified amount
  // If the resulting latitude would be invalid, do not modify it,
  // and instead throw an IllegalArgumentException
  void moveNorth(double amount);

  // Shift the location south by the specified amount
  // If the resulting latitude would be invalid, do not modify it,
  // and instead throw an IllegalArgumentException
  void moveSouth(double amount);

  // Shift the location east by the specified amount
  // If the resulting longitude would be invalid, do not modify it,
  // and instead throw an IllegalArgumentException

  void moveEast(double amount);

  // Shift the location west by the specified amount
  // If the resulting longitude would be invalid, do not modify it,
  // and instead throw an IllegalArgumentException
  void moveWest(double amount);
}
