package okay.kernel;

/**
 * WHAT AN IMPLEMENTATION OFFERS CODE WRITTEN ONCE: its programs, built and bound; an operation, to the nearest
 * handler of its effect; a handler, a delimiter; the minimal control, {@code shift0} and {@code $}; failures,
 * finalizers and regions cancellation waits for. The answer types are only parameters here: the fronts check them.
 * Each implementation's bridge implements it on its own machine; the standard handlers, written once over it, work
 * in every one
 */
public interface Effects {
  <A> Prog<A> pure(A a);

  <A, B> Prog<B> flatMap(Prog<A> p, Fn<A, Prog<B>> f);

  default <A, B> Prog<B> map(Prog<A> p, Fn<A, B> f) {
    return flatMap(p, a -> pure(f.apply(a)));
  }

  /** a program built when it is run: a tail call */
  <A> Prog<A> delay(Thunk<Prog<A>> p);

  /** an operation, to the nearest handler of its effect in force */
  <X> Prog<X> perform(Op<X> op);

  /** {@code body} under the handler {@code h}: a delimiter, its effect taken off */
  <A, R> Prog<R> handle(Handler<A, R> h, Program<A> body);

  /** the rest up to the nearest delimiter, the delimiter included, as {@code k}; {@code body} runs outside it, in its
   * place */
  <A, I, O> Prog<A> shift0(Fn<Fn<A, Prog<I>>, Prog<O>> body);

  /** {@code body} under a fresh delimiter, its value given to {@code k}: {@code body $ k} */
  <A, I, O> Prog<O> dollar(Program<A> body, Fn<A, Prog<I>> k);

  /** a failure: an exception, as a program */
  <A> Prog<A> failed(Throwable e);

  /** {@code body}, a failure of it given to {@code h} */
  <A> Prog<A> recover(Program<A> body, Fn<Throwable, Prog<A>> h);

  /** {@code body}, then {@code finalizer} — after a value, a failure or a cancellation alike */
  <A> Prog<A> ensuring(Program<A> body, Program<Void> finalizer);

  /** {@code body}, a cancellation waiting till it ends */
  <A> Prog<A> uncancelable(Program<A> body);
}
