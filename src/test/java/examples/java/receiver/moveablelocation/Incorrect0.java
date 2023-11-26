package examples.java.receiver.moveablelocation;

public class Incorrect0 implements IMovableLocation {

  private double latitude;
  private double longitude;

  public Incorrect0(double setLatitude, double setLongitude) {
    if (setLatitude < -90.0 || setLatitude > 90.0) {
      throw new IllegalArgumentException();
    }
    if (setLongitude < -180.0 || setLongitude > 180.0) {
      throw new IllegalArgumentException();
    }
    latitude = setLatitude;
    longitude = setLongitude;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  @Override
  public void moveNorth(double amount) {
    double newLatitude = latitude + amount;
    if (newLatitude > 90.0) {
      throw new IllegalArgumentException();
    }
    latitude = newLatitude;
  }

  @Override
  public void moveSouth(double amount) {
    double newLatitude = latitude - amount;
    if (newLatitude < -90.0) {
      throw new IllegalArgumentException();
    }
    latitude = newLatitude;
  }

  @Override
  public void moveEast(double amount) {
    double newLongitude = longitude + amount;
    if (newLongitude > 180.9) {
      throw new IllegalArgumentException();
    }
    longitude = newLongitude;
  }

  @Override
  public void moveWest(double amount) {
    double newLongitude = longitude - amount;
    if (newLongitude < -180.0) {
      throw new IllegalArgumentException();
    }
    longitude = newLongitude;
  }
}
