package okay.kernel;

/**
 * A STEP of a computation handed out: where it came to — a value, an operation it needs, a failure, a cancellation.
 * The protocol between implementations: one runs its program on its own machine and hands out what it cannot answer
 */
public sealed interface Step<A> permits Step.Done, Step.Yield, Step.Failed, Step.Cancelled {
  /** it came to a value */
  record Done<A>(A value) implements Step<A> {}

  /** it needs {@code op}, answered by its host; {@code k} the rest — multi-shot, later, from another thread too */
  record Yield<X, A>(Op<X> op, Resume<X, A> k) implements Step<A> {}

  /** it failed: an exception not caught inside, as data — not thrown through another's machine */
  record Failed<A>(Throwable error) implements Step<A> {}

  /** cancelled from outside, its finalizers run */
  record Cancelled<A>() implements Step<A> {}
}
