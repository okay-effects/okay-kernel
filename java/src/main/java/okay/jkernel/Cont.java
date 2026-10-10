package okay.jkernel;

import okay.kernel.Effect;
import okay.kernel.Fn;
import okay.kernel.Handler;
import okay.kernel.Monad;
import okay.kernel.Op;
import okay.kernel.Prog;
import okay.kernel.Program;
import okay.kernel.std.Shift0;

/**
 * DELIMITED CONTROL, THE ANSWER TYPE MODIFIED, STATICALLY — okay-kernel's {@code okay.Cont}, in Java: Materzok–
 * Biernacki's shift0 and {@code $}, a program's answer moving from {@code I} to {@code O} in its type, the answers
 * themselves programs (the CPS hierarchy); and Danvy–Filinski's shift and reset on them, typed as Asai–Kameyama's at
 * answers of no move — Scala's {@code Cont.Flat[I, O, A]}, here written out: {@code Cont<Cont<I, I, I>, Cont<O, O, O>,
 * A>}. One delimiter for both, a handler of the effect {@code Reset0} and the API's {@link Shift0}: Scala's {@code Cont}
 * the same — a shift0 of one goes to a delimiter of the other
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class Cont<I, O, A> {
  final Program<A> p;

  Cont(Program<A> p) {
    this.p = p;
  }

  public <I2, B> Cont<I2, O, B> flatMap(Fn<A, Cont<I2, I, B>> f) {
    return new Cont<>(m -> m.flatMap(p.in(m), a -> f.apply(a).p.in(m)));
  }

  public <B> Cont<I, O, B> map(Fn<A, B> f) {
    return new Cont<>(m -> m.map(p.in(m), f));
  }

  /** this program as a kernel {@code Program}, its answers said by its type only: to another language's {@code Cont} */
  public Program<A> program() {
    return p;
  }

  /** a kernel {@code Program} of another language's {@code Cont}, its answers as said: its delimiters are this one's */
  public static <I, O, A> Cont<I, O, A> from(Program<A> p) {
    return new Cont<>(p);
  }

  private static final Effect effect = Effect.of("Reset0");

  private static <A> Handler<A, Object> delimiter(Fn<A, Program<Object>> back) {
    return new Handler<>() {
      public Effect effect() { return effect; }
      public Prog<Object> ret(A a, Monad m) { return back.apply(a).in(m); }
      public <X> Prog<Object> apply(Op<X> op, Fn<X, Prog<Object>> k, Monad m) {
        return ((Shift0<X, Object>) op).f().apply(k).in(m);
      }
    };
  }

  private static <A> Program<A> inner(Object c) {
    return ((Cont) c).p;
  }

  public static <S, A> Cont<S, S, A> pure(A a) {
    return new Cont<>(m -> m.pure(a));
  }

  /** the rest up to the nearest delimiter, the delimiter included, to {@code f}, run in its place: its answer {@code O} */
  public static <I1, O1, X1, O, A> Cont<Cont<I1, O1, X1>, O, A> shift0(Fn<Fn<A, Cont<I1, O1, X1>>, O> f) {
    return new Cont<>(m -> m.perform(new Shift0<A, Object>(effect,
        k -> inner(f.apply(a -> new Cont<>(mm -> (Prog<X1>) (Prog) k.apply(a)))))));
  }

  /** {@code ret $ body}: {@code body} under a delimiter, its value to {@code ret} */
  public static <I, I2, O2, B, A> Cont<I2, O2, B> dollar(Fn<A, I> ret, Cont<I, Cont<I2, O2, B>, A> body) {
    return new Cont<>(m -> (Prog<B>) (Prog) m.handle(Cont.<A>delimiter(a -> inner(ret.apply(a))), body.p));
  }

  /** {@code body} under a delimiter, its value the answer */
  public static <J, I2, O2, B, A> Cont<I2, O2, B> reset0(Cont<Cont<J, J, A>, Cont<I2, O2, B>, A> body) {
    return new Cont<>(m -> (Prog<B>) (Prog) m.handle(Cont.<A>delimiter(a -> pm -> pm.pure(a)), body.p));
  }

  /** a program closed, run: its answer */
  public static <A> A value(Cont<A, A, A> c) {
    return Program.run(c.p, Monads.steps());
  }

  public static <A> A value(Cont<A, A, A> c, Monad m) {
    return Program.run(c.p, m);
  }

  /** a closed program as a kernel {@code Program}: under any handler, in any language */
  public static <A> Program<A> program(Cont<A, A, A> c) {
    return c.p;
  }

  // DANVY–FILINSKI'S, ON THE SAME DELIMITERS: Flat<I, O, A> = Cont<Cont<I, I, I>, Cont<O, O, O>, A>

  /** {@code shift(k -> body)}: {@code k} the rest up to the nearest reset, answering {@code I} at the hole; the body
   * under the reset again, at its final answer {@code O} — the answer moved from {@code I} to {@code O} */
  public static <I, O, A> Cont<Cont<I, I, I>, Cont<O, O, O>, A> shift(
      Fn<Fn<A, Cont<Cont<O, O, O>, Cont<O, O, O>, I>>, Cont<Cont<O, O, O>, Cont<O, O, O>, O>> f) {
    return Cont.<I, I, I, Cont<O, O, O>, A>shift0(k -> Cont.<O, O, O, O, O>reset0(
        (Cont) f.apply(a -> Cont.<O, I>closed(k.apply(a)))));
  }

  /** {@code reset(body)}: the body at the answer {@code S}, its value; the reset answers what the body moved it to,
   * {@code O} — and as a program it is closed: at any answer {@code T} around it */
  public static <T, S, O> Cont<Cont<T, T, T>, Cont<T, T, T>, O> reset(Cont<Cont<S, S, S>, Cont<O, O, O>, S> body) {
    return closed(Cont.<S, O, O, O, S>reset0((Cont) body));
  }

  /** a program whose captures cannot leave it — a reset, a continuation {@code k(x)} — at any answer around it */
  public static <T, A> Cont<Cont<T, T, T>, Cont<T, T, T>, A> closed(Cont<?, ?, A> c) {
    return (Cont) c;
  }

  /** a flat program's value, now: a reset, or {@code k(x)} — it holds its delimiter, so it runs on its own */
  public static <T, I> I eval(Cont<Cont<T, T, T>, Cont<T, T, T>, I> c) {
    return Program.run(c.p, Monads.steps());
  }

  public static <T, I> I eval(Cont<Cont<T, T, T>, Cont<T, T, T>, I> c, Monad m) {
    return Program.run(c.p, m);
  }

  /** the delimiters of answer {@code R}: {@code Cont.<Integer>at()} */
  public static <R> At<R> at() {
    return new At<>();
  }

  /** THE DELIMITERS OF ANSWER {@code R}: their operations, {@code R} given once — at a call, only the value's type */
  public static final class At<R> {
    At() {}

    /** shift0, the answer kept */
    public <A> Cont<Cont<R, R, R>, Cont<R, R, R>, A> shift0(Fn<Fn<A, Cont<R, R, R>>, Cont<R, R, R>> f) {
      return Cont.<R, R, R, Cont<R, R, R>, A>shift0(f);
    }

    /** shift0, the answer moved to {@code O} */
    public <A, O> Cont<Cont<R, R, R>, O, A> shift0To(Fn<Fn<A, Cont<R, R, R>>, O> f) {
      return Cont.<R, R, R, O, A>shift0(f);
    }

    /** a delimiter: the body's value the answer */
    public Cont<R, R, R> reset0(Cont<Cont<R, R, R>, Cont<R, R, R>, R> body) {
      return Cont.<R, R, R, R, R>reset0(body);
    }

    /** {@code ret $ body} */
    public <A> Cont<R, R, R> dollar(Fn<A, Cont<R, R, R>> ret, Cont<Cont<R, R, R>, Cont<R, R, R>, A> body) {
      return Cont.<Cont<R, R, R>, R, R, R, A>dollar(ret, body);
    }

    public <A> Cont<R, R, A> pure(A a) {
      return Cont.pure(a);
    }

    /** shift, the answer kept */
    public <A> Cont<Cont<R, R, R>, Cont<R, R, R>, A> shift(
        Fn<Fn<A, Cont<Cont<R, R, R>, Cont<R, R, R>, R>>, Cont<Cont<R, R, R>, Cont<R, R, R>, R>> f) {
      return Cont.<R, R, A>shift(f);
    }

    /** shift, the answer moved from {@code R} to {@code O} */
    public <A, O> Cont<Cont<R, R, R>, Cont<O, O, O>, A> shiftTo(
        Fn<Fn<A, Cont<Cont<O, O, O>, Cont<O, O, O>, R>>, Cont<Cont<O, O, O>, Cont<O, O, O>, O>> f) {
      return Cont.<R, O, A>shift(f);
    }

    /** a reset: the body at the answer {@code R}; the reset answers {@code O}, what the body moved it to */
    public <O> Cont<Cont<O, O, O>, Cont<O, O, O>, O> reset(Cont<Cont<R, R, R>, Cont<O, O, O>, R> body) {
      return Cont.<O, R, O>reset(body);
    }
  }
}
