package okay.jkernel.lexical;

import okay.kernel.Effect;
import okay.kernel.Fn;
import okay.kernel.std.Shift;
import okay.kernel.std.Shift0;

/** A RESET'S PROMPT, at the body's level {@code M}, the reset's {@code L}, its answer {@code R}: shift0 and shift to
 * it, by identity — past any reset nearer */
public final class Prompt<L, M, R> {
  private final Effect e;

  Prompt(Effect e) {
    this.e = e;
  }

  /** the rest up to this reset, the reset included, to the clause, run in its place — outside it */
  public <X> Prog<M, X> shift0(Fn<Fn<X, Prog<L, R>>, Prog<L, R>> clause) {
    return Prog.of(m -> m.perform(new Shift0<X, R>(e, k -> clause.apply(x -> Prog.of(mm -> k.apply(x))))));
  }

  /** the same, the clause under the reset again (Danvy–Filinski) */
  public <X> Prog<M, X> shift(Fn<Fn<X, Prog<L, R>>, Prog<M, R>> clause) {
    return Prog.of(m -> m.perform(new Shift<X, R>(e, k -> clause.apply(x -> Prog.of(mm -> k.apply(x))))));
  }
}
