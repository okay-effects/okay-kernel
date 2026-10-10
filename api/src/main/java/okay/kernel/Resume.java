package okay.kernel;

/**
 * THE REST of a computation suspended on an operation: multi-shot, at any time, from any thread. Each call a step of
 * that path; the others not touched
 */
public interface Resume<X, A> {
  /** on, the operation's value {@code x} */
  Step<A> resume(X x);

  /** on, as if the operation threw {@code e} where it was performed: the computation may catch it */
  Step<A> fail(Throwable e);

  /** ended: its finalizers run — programs themselves, so a step: yields while they need operations, then
   * {@code Cancelled}, or {@code Failed} if one failed. Not catchable */
  Step<A> cancel();
}
