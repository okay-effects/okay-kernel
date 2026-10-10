package okay.jkernel.lexical;

import okay.jkernel.Monads;
import okay.jkernel.std.Control;
import okay.jkernel.std.Handlers;
import okay.kernel.Effect;
import okay.kernel.Fn;
import okay.kernel.Handler;
import okay.kernel.Monad;
import okay.kernel.Program;
import okay.kernel.Thunk;
import okay.kernel.std.Final;
import okay.kernel.std.Logged;
import okay.kernel.std.Result;

/**
 * THE KERNEL TYPED BY LEVELS: a program is at a level {@code L}; a handler's body is a generic method,
 * {@code <M extends L> in(...)}, so its level {@code M} is fresh — a capability, given at {@code M}, does not leave the
 * body, statically. Capabilities are LEXICAL: each run of a handler an effect of its own ({@code Effect.apply},
 * generative), so a capability reaches its own handler, past any other of its kind nearer. A {@code Prog} is a kernel
 * {@code Program}: run on any monad, mixed with programs of any language. okay-kernel's {@code okay.lexical}, in Java
 */
public final class Prog<L, A> implements Program<A> {
  private final Program<A> p;

  private Prog(Program<A> p) {
    this.p = p;
  }

  public okay.kernel.Prog<A> in(Monad m) {
    return p.in(m);
  }

  /** {@code f} on this program's value: a program of this level, or of one further out */
  public <B> Prog<L, B> flatMap(Fn<? super A, ? extends Prog<? super L, B>> f) {
    return new Prog<>(m -> m.flatMap(p.in(m), a -> f.apply(a).in(m)));
  }

  public <B> Prog<L, B> map(Fn<? super A, ? extends B> f) {
    return new Prog<>(m -> m.map(p.in(m), f::apply));
  }

  public <B> Prog<L, B> andThen(Prog<? super L, B> next) {
    return flatMap(a -> next);
  }

  /** this program at a level further in */
  @SuppressWarnings("unchecked")
  public <M extends L> Prog<M, A> at() {
    return (Prog<M, A>) this;
  }

  public static <L, A> Prog<L, A> pure(A a) {
    return new Prog<>(m -> m.pure(a));
  }

  public static <L, A> Prog<L, A> delay(Thunk<Prog<L, A>> c) {
    return new Prog<>(m -> m.delay(() -> c.get().in(m)));
  }

  /** a program of another language, at a level said */
  public static <L, A> Prog<L, A> of(Program<A> p) {
    return new Prog<>(p);
  }

  /** a program at the top, run: by the machine of steps, or another monad */
  public static <A> A run(Prog<Top, A> p) {
    return Program.run(p, Monads.steps());
  }

  public static <A> A run(Prog<Top, A> p, Monad m) {
    return Program.run(p, m);
  }

  /** a handler of a fresh effect, one's own: its handler made for the effect, the body given the capability */
  public static <L, A, R> Prog<L, R> handle(Fn<Effect, Handler<A, R>> handler, Body<L, A> body) {
    return new Prog<>(m -> {
      Effect e = Effect.apply("lexical");
      return m.handle(handler.apply(e), body.in(new Cap<>(e)));
    });
  }

  /** a delimiter of a fresh prompt, its body given the prompt: its body's value the answer */
  public static <L, R> Prog<L, R> reset(ResetBody<L, R> body) {
    return new Prog<>(m -> {
      Effect e = Effect.apply("Prompt");
      return m.handle(Control.<R>delimiter(e), body.in(new Prompt<L, L, R>(e)));
    });
  }

  /** a log of {@code W}s: the log, first told first, and the value */
  public static <L, W, A> Prog<L, Logged<W, A>> writer(WriterBody<L, W, A> body) {
    return new Prog<>(m -> {
      Effect e = Effect.apply("Writer");
      return m.handle(Handlers.<W, A>writer(e), body.in(new Caps.WriterCap<>(e)));
    });
  }

  /** a state of a cell, one a run: the final state and the value */
  public static <L, S, A> Prog<L, Final<S, A>> state(S s0, StateBody<L, S, A> body) {
    return new Prog<>(m -> {
      Effect e = Effect.apply("State");
      return m.handle(Handlers.<S, A>state(e, s0), body.in(new Caps.StateCap<>(e)));
    });
  }

  /** the value, or the first failure raised */
  public static <L, E, A> Prog<L, Result<E, A>> attempt(ThrowsBody<L, E, A> body) {
    return new Prog<>(m -> {
      Effect e = Effect.apply("Throws");
      return m.handle(Handlers.<E, A>throwsOf(e), body.in(new Caps.ThrowsCap<>(e)));
    });
  }
}
