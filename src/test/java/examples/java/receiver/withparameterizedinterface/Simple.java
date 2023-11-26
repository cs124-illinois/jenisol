package examples.java.receiver.withparameterizedinterface;

public interface Simple<E> {
  void set(E element);

  E get();
}
