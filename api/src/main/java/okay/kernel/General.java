package okay.kernel;

/** a handler with a clause: an operation and {@code k}, the rest of the body up to the handler, the handler included
 * (deep), any number of times; its programs built in {@code e}, the implementation's. Its return, unless it is also
 * {@link Returning}, a program */
public interface General<A, R> extends Handler<A, R> {
  Prog<R> ret(A a, Effects e);

  <X> Prog<R> apply(Op<X> op, Fn<X, Prog<R>> k, Effects e);
}
