package okay.kernel;

/** a function: a lambda of any of the languages */
@FunctionalInterface
public interface Fn<A, B> {
  B apply(A a);
}
