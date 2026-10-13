package okay.kernel;

/**
 * WHAT AN IMPLEMENTATION OFFERS CODE WRITTEN ONCE: its programs, built and bound; an operation, to the nearest
 * handler of its effect; a handler, a delimiter; failures, finalizers and regions cancellation waits for. The answer
 * types are only parameters here: the fronts check them. Each implementation's bridge implements it on its own
 * machine; the standard handlers, written once over it, work in every one. Delimited control ({@code shift0},
 * {@code $}) is apart, {@link Control}: an implementation has it or not
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

  /** {@code body} under {@code h}, an INTERCEPTOR of an effect handled outside it: the body's operations of
   * {@code h.effect()} go to {@code h}; its clauses' own to the handler outside — so it changes what an effect does
   * in the body, not which effects the body has. The handler is a delimiter as any */
  default <A> Prog<A> intercept(Handler<A, A> h, Program<A> body) {
    return handle(h, body);
  }

  /** a failure: an exception, as a program */
  <A> Prog<A> failed(Throwable e);

  /** {@code body}, a failure of it given to {@code h} */
  <A> Prog<A> recover(Program<A> body, Fn<Throwable, Prog<A>> h);

  /** {@code body}, then {@code finalizer} — after a value, a failure or a cancellation alike */
  <A> Prog<A> ensuring(Program<A> body, Program<Void> finalizer);

  /** {@code body}, a cancellation waiting till it ends */
  <A> Prog<A> uncancelable(Program<A> body);

  // -- the protocol's two doors, every implementation's

  /** EXPORT: a program, handed out — run on this implementation's machine, stepping out where it needs what it cannot
   * answer; its row {@code effects} as known where it was written */
  <A> Computation<A> computation(Program<A> program, java.util.Set<Type> effects);

  /** IMPORT: another's program as one of this implementation — its operations answered here */
  <A> Prog<A> foreign(Computation<A> computation);
}
