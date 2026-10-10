package okay.kernel;

/** a value computed when asked */
@FunctionalInterface
public interface Thunk<A> {
  A get();
}
