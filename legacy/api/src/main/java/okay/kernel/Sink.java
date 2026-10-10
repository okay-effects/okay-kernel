package okay.kernel;

/** a value taken, nothing given back: a callback */
@FunctionalInterface
public interface Sink<A> {
  void accept(A a);
}
