package okay.jkernel.std;

import okay.kernel.std.*;

import okay.kernel.Effect;
import okay.kernel.Fn;
import okay.kernel.Handler;
import okay.kernel.Monad;
import okay.kernel.Op;
import okay.kernel.Prog;
import okay.kernel.Program;

/** DELIMITED CONTROL ON THE KERNEL: a reset is a handler of an effect fresh each run — its prompt — and a shift an
 * operation of it. {@code shift0}'s clause runs outside the delimiter (Materzok–Biernacki), {@code shift}'s under it
 * again (Danvy–Filinski) */
@SuppressWarnings("unchecked")
public final class Control {
  private Control() {}

  /** a delimiter's identity: the rest up to it captured by its operations */
  public static final class Prompt<R> {
    final Effect effect;

    Prompt(Effect effect) { this.effect = effect; }

    /** the rest up to this prompt's delimiter, the delimiter included, to the clause, run in its place, outside */
    public <A> Program<A> shift0(Fn<Fn<A, Prog<R>>, Program<R>> f) {
      return m -> m.perform(new Shift0<A, R>(effect, f));
    }

    /** the same, the clause under the delimiter again */
    public <A> Program<A> shift(Fn<Fn<A, Prog<R>>, Program<R>> f) {
      return m -> m.perform(new Shift<A, R>(effect, f));
    }
  }

  /** a delimiter of a fresh prompt, its body's value the answer */
  public static <R> Program<R> reset(Fn<Prompt<R>, Program<R>> body) {
    return m -> {
      Prompt<R> p = new Prompt<>(Effect.apply("Prompt"));
      return m.handle(delimiter(p.effect), body.apply(p));
    };
  }

  static <R> Handler<R, R> delimiter(Effect e) {
    return new Handler<>() {
      public Effect effect() { return e; }
      public Prog<R> ret(R a, Monad m) { return m.pure(a); }
      public <X> Prog<R> apply(Op<X> op, Fn<X, Prog<R>> k, Monad m) {
        if (op instanceof Shift0<?, ?> s) return ((Shift0<X, R>) s).f().apply(k).in(m);
        return m.handle(this, ((Shift<X, R>) op).f().apply(k));
      }
    };
  }
}
