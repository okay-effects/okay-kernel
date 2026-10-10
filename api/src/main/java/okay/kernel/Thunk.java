package okay.kernel;

/** a value made when asked: a lambda of any of the languages */
@FunctionalInterface
public interface Thunk<A> {
  A get();
}
