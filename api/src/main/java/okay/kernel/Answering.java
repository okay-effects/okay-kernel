package okay.kernel;

/** a TAIL-RESUMPTIVE handler: each clause {@code k(value(op))} — answered in place, no capture */
public interface Answering<A, R> extends Handler<A, R> {
  <X> X value(Op<X> op);

  @Override
  default <X> Prog<R> apply(Op<X> op, Fn<X, Prog<R>> k, Monad m) {
    return k.apply(value(op));
  }
}
