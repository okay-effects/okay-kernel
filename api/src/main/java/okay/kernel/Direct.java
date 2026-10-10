package okay.kernel;

/**
 * THE FAST PATH, OPTIONAL: an implementation's own programs, untyped, so code written once is built in its nodes
 * directly — a bind's function passed as it is, no {@link Prog} around each node. An interface of its own, not an
 * {@link Effects}: an implementation offering it implements both, and code written once asks
 * {@code e instanceof Direct}. Its values are the implementation's programs; {@code prog} and {@code unprog} cross to
 * the kernel's where they meet them: a clause, another's program
 */
public interface Direct {
  Object pure(Object a);

  Object flatMap(Object p, Fn<Object, Object> f);

  Object map(Object p, Fn<Object, Object> f);

  Object delay(Thunk<Object> p);

  Object perform(Op<?> op);

  Object handle(Handler<?, ?> h, Program<?> body);

  /** the implementation's program as the kernel's */
  <A> Prog<A> prog(Object p);

  /** the kernel's program, of this implementation, as its own */
  Object unprog(Prog<?> p);
}
