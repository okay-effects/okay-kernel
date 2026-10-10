package okay.kernel;

/** A HANDLER of an effect: the body's value {@code A} through {@code ret} to its answer {@code R}; an operation through
 * its clause, {@code k} the rest of the body up to the handler, the handler included (deep), any number of times, from
 * anywhere — typed by the operation's value. The clause runs outside the handler, in its place: an operation of the
 * same effect there goes to the next one out. Its programs built in the monad given */
public interface Handler<A, R> {
  Effect effect();

  Prog<R> ret(A a, Monad m);

  <X> Prog<R> apply(Op<X> op, Fn<X, Prog<R>> k, Monad m);
}
