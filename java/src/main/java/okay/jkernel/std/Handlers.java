package okay.jkernel.std;

import okay.kernel.std.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import okay.kernel.Answering;
import okay.kernel.Effect;
import okay.kernel.Fn;
import okay.kernel.Handler;
import okay.kernel.Monad;
import okay.kernel.Op;
import okay.kernel.Prog;

/** THE STANDARD HANDLERS, typed values: any program's operations of their effects, on any monad — their answers
 * Java's types */
@SuppressWarnings("unchecked")
public final class Handlers {
  private Handlers() {}

  /** the body's value with its log, each tell in front of the rest's */
  public static <W, A> Handler<A, Logged<W, A>> writer(Effect e) {
    return new Handler<>() {
      public Effect effect() { return e; }
      public Prog<Logged<W, A>> ret(A a, Monad m) { return m.pure(Logged.of(a)); }
      public <X> Prog<Logged<W, A>> apply(Op<X> op, Fn<X, Prog<Logged<W, A>>> k, Monad m) {
        W w = ((Tell<W>) op).w();
        return m.map(k.apply(null), l -> l.told(w));
      }
    };
  }

  /** the body's value an {@code Ok}, a raise's a {@code Failed} */
  public static <E, A> Handler<A, Result<E, A>> throwsOf(Effect e) {
    return new Handler<>() {
      public Effect effect() { return e; }
      public Prog<Result<E, A>> ret(A a, Monad m) { return m.pure(Result.ok(a)); }
      public <X> Prog<Result<E, A>> apply(Op<X> op, Fn<X, Prog<Result<E, A>>> k, Monad m) {
        return m.pure(Result.failed(((Raise<E, X>) op).error()));
      }
    };
  }

  /** a cell, one a run of the handler: the final state with the value */
  public static <S, A> Handler<A, Final<S, A>> state(Effect e, S s0) {
    return new Answering<>() {
      private S s = s0;
      public Effect effect() { return e; }
      public Prog<Final<S, A>> ret(A a, Monad m) { return m.pure(new Final<>(s, a)); }
      public <X> X value(Op<X> op) {
        if (op instanceof Put<?> p) {
          s = (S) p.s();
          return null;
        }
        return (X) s;
      }
    };
  }

  /** every value the body comes to, one a path through its choices */
  public static <A> Handler<A, List<A>> choose() {
    return new Handler<>() {
      public Effect effect() { return Effects.CHOOSE; }
      public Prog<List<A>> ret(A a, Monad m) { return m.pure(List.of(a)); }
      public <X> Prog<List<A>> apply(Op<X> op, Fn<X, Prog<List<A>>> k, Monad m) {
        return from(((Among<X>) op).as(), 0, new ArrayList<>(), k, m);
      }
      private <X> Prog<List<A>> from(List<X> as, int i, List<A> acc, Fn<X, Prog<List<A>>> k, Monad m) {
        if (i == as.size()) return m.pure(acc);
        return m.flatMap(k.apply(as.get(i)), r -> {
          List<A> next = new ArrayList<>(acc);
          next.addAll(r);
          return from(as, i + 1, next, k, m);
        });
      }
    };
  }

  /** the first value the body comes to, its choices tried in order, the rest not tried */
  public static <A> Handler<A, Optional<A>> first() {
    return new Handler<>() {
      public Effect effect() { return Effects.CHOOSE; }
      public Prog<Optional<A>> ret(A a, Monad m) { return m.pure(Optional.of(a)); }
      public <X> Prog<Optional<A>> apply(Op<X> op, Fn<X, Prog<Optional<A>>> k, Monad m) {
        return from(((Among<X>) op).as(), 0, k, m);
      }
      private <X> Prog<Optional<A>> from(List<X> as, int i, Fn<X, Prog<Optional<A>>> k, Monad m) {
        if (i == as.size()) return m.pure(Optional.empty());
        return m.flatMap(k.apply(as.get(i)), r -> r.isPresent() ? m.pure(r) : from(as, i + 1, k, m));
      }
    };
  }

  /** awaits answered at once only — a callback called by its registration. One called later needs the run's top,
   * {@code Async.start} */
  public static <A> Handler<A, A> async() {
    return new Handler<>() {
      public Effect effect() { return Effects.ASYNC; }
      public Prog<A> ret(A a, Monad m) { return m.pure(a); }
      public <X> Prog<A> apply(Op<X> op, Fn<X, Prog<A>> k, Monad m) {
        Async.Cell<X> cell = new Async.Cell<>();
        ((Await<X>) op).register().accept(cell);
        if (!cell.answered) throw new IllegalStateException("an await answered later, under no Async.start");
        if (cell.failure != null) throw Async.sneaky(cell.failure);
        return k.apply(cell.value);
      }
    };
  }
}
