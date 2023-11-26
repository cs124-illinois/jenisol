package examples.java.receiver.withparameterizedinterface;

public class Correct<E> implements Simple<E> {
  private E element;

  @Override
  public void set(E setElement) {
    element = setElement;
  }

  @Override
  public E get() {
    return element;
  }
}
