package examples.java.receiver.moveablelocation;

import edu.illinois.cs.cs125.jenisol.core.EdgeType;
import edu.illinois.cs.cs125.jenisol.core.FixedParameters;
import edu.illinois.cs.cs125.jenisol.core.RandomParameters;
import edu.illinois.cs.cs125.jenisol.core.RandomType;
import edu.illinois.cs.cs125.jenisol.core.SimpleType;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Correct implements IMovableLocation {

  private double latitude;
  private double longitude;

  public Correct(double setLatitude, double setLongitude) {
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

  @SimpleType
  private static final double[] SIMPLE_DOUBLES = new double[] {-90.0, -89.9, 89.9, 90.0};

  @EdgeType
  private static final double[] EDGE_DOUBLES =
      new double[] {-180.1, -180.0, -179.9, -90.1, 90.1, 179.9, 180.0, 180.1};

  @RandomType
  private static double randomPosition(Random random) {
    return (random.nextDouble() * 179.8) - 89.9;
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
    if (newLongitude > 180.0) {
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

  @FixedParameters("*")
  private static final List<Double> FIXED = Arrays.asList(0.1, 90.0, 180.0);

  @RandomParameters("moveNorth")
  private double randomNorth(Random random) {
    if (random.nextBoolean()) {
      return 90.0 - latitude;
    }
    return random.nextDouble() * 90.0;
  }

  @RandomParameters("moveSouth")
  private double randomSouth(Random random) {
    if (random.nextBoolean()) {
      return 90.0 + latitude;
    }
    return random.nextDouble() * 90.0;
  }

  @RandomParameters("moveEast")
  private double randomEast(Random random) {
    if (random.nextBoolean()) {
      return 180.0 - longitude;
    }
    return random.nextDouble() * 180.0;
  }

  @RandomParameters("moveWest")
  private double randomWest(Random random) {
    if (random.nextBoolean()) {
      return 180.0 + longitude;
    }
    return random.nextDouble() * 180.0;
  }
}
