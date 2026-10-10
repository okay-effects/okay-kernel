package okay.kernel;

/** a handler answering in place: each operation's value by the operation alone, no capture — a reader, a cell */
public interface Answering<A, R> extends Handler<A, R> {
  <X> X value(Op<X> op);
}
