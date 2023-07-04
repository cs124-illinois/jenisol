package examples.java.receiver.kotlinfauxproperty;

public class Incorrect {
  private int counter = 0;

  public void setCounter(int setCounter) {
    counter = setCounter;
  }

  public int getCounter() {
    return counter + 1;
  }
}
