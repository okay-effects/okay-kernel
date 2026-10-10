package okay.kernel;

/** A MONAD: an implementation of the kernel — its programs, and their run */
public interface Monad {
  <A> Prog<A> pure(A a);

  <A, B> Prog<B> flatMap(Prog<A> p, Fn<A, Prog<B>> f);

  default <A, B> Prog<B> map(Prog<A> p, Fn<A, B> f) {
    return flatMap(p, a -> pure(f.apply(a)));
  }

  /** a program built when it is run: a tail call */
  <A> Prog<A> delay(Thunk<Prog<A>> p);

  /** an operation, to the nearest handler of its effect in force */
  <X> Prog<X> perform(Op<X> op);

  /** a program under a handler: built in this monad when the run enters it */
  <A, R> Prog<R> handle(Handler<A, R> h, Program<A> body);

  /** the program's value; a failure, thrown — no handler for an operation among them */
  <A> A run(Prog<A> p);
}
