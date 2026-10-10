package okay.kernel;

/**
 * THE FAST PATH, OPTIONAL: an implementation's own programs, untyped, so code written once is built in its nodes
 * directly — a bind's function passed as it is, no {@link Prog} around each node. An interface of its own, not an
 * {@link Effects}: an implementation offering it implements both, and code written once asks
 * {@code e instanceof Direct}. Its values are the implementation's programs; {@code prog} and {@code unprog} cross to
 * the kernel's where they meet them: a clause, another's program. Its names its own ({@code directPure}, …): one class
 * implementing both it and {@link Effects} has no two methods of one erasure
 */
public interface Direct {
  Object directPure(Object a);

  Object directFlatMap(Object p, Fn<Object, Object> f);

  Object directMap(Object p, Fn<Object, Object> f);

  Object directDelay(Thunk<Object> p);

  Object directPerform(Op<?> op);

  Object directHandle(Handler<?, ?> h, Program<?> body);

  /** the implementation's program as the kernel's */
  <A> Prog<A> prog(Object p);

  /** the kernel's program, of this implementation, as its own */
  Object unprog(Prog<?> p);
}
