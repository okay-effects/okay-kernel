package okay.kernel;

/** A PROGRAM: a recipe, built in any monad — in every language and implementation of the kernel */
@FunctionalInterface
public interface Program<A> {
  Prog<A> in(Monad m);

  /** a program run in a monad: its value */
  static <A> A run(Program<A> p, Monad m) {
    return m.run(p.in(m));
  }
}
