package okay.kernel;

/** a handler that never resumes: an operation ends the body, the handler's answer the operation's — a throw */
public interface Aborting<A, R> extends Handler<A, R> {
  <X> R abort(Op<X> op);
}
