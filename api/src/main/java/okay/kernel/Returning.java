package okay.kernel;

/** a handler whose return is a value, no program: the body's value to the answer */
public interface Returning<A, R> extends Handler<A, R> {
  R answer(A a);
}
