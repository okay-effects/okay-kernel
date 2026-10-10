package okay.kernel;

/** a handler whose RETURN IS A VALUE, no program: {@code ret(a, m)} is {@code m.pure(answer(a))} — so a monad may take
 * the value itself, no program made and run for it (okay-core's handlers return so: a monad on it, its own handler) */
public interface Returning<A, R> extends Handler<A, R> {
  R answer(A a);

  @Override
  default Prog<R> ret(A a, Monad m) {
    return m.pure(answer(a));
  }
}
