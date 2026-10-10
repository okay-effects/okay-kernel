package okay.kernel;

/** a function: a lambda */
@FunctionalInterface
public interface Fn<A, B> {
  B apply(A a);
}
