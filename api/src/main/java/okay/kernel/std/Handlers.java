package okay.kernel.std;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import okay.kernel.Aborting;
import okay.kernel.Answering;
import okay.kernel.Effects;
import okay.kernel.Fn;
import okay.kernel.General;
import okay.kernel.Key;
import okay.kernel.Op;
import okay.kernel.Prog;
import okay.kernel.Program;
import okay.kernel.Returning;
import okay.kernel.Translating;
import okay.kernel.Same;
import okay.kernel.Type;
import okay.kernel.std.Results.Err;
import okay.kernel.std.Results.Final;
import okay.kernel.std.Results.Gen;
import okay.kernel.std.Results.Logged;
import okay.kernel.std.Results.Ok;
import okay.kernel.std.Results.Result;

/**
 * THE STANDARD HANDLERS, WRITTEN ONCE over {@link Effects}: every implementation runs them — the first three shapes
 * its own handlers at no cost, the general ones by its {@code Effects}. Each a {@link Program}: built where it is run
 */
public final class Handlers {
  private Handlers() {}

  /** a handler of the type {@code t} answering in place and returning a value: one shape-object for a lambda pair */
  private interface Cell<A, R> extends Answering<A, R>, Returning<A, R> {}

  // -- State

  /** STATE AS A CELL: answered in place, one cell a run of the handler; the final state with the value */
  public static <S, A> Program<Final<S, A>> state(Key<State<S>> t, S s0, Program<A> body) {
    return e -> e.handle(new Cell<A, Final<S, A>>() {
      private S s = s0;
      public Key<?> key() { return t; }
      public Final<S, A> answer(A a) { return new Final<>(s, a); }
      public <X> X value(Op<X> op) {
        return State.narrow(t, op).fold(new State.Cases<S, X, X>() {
          public X get(Same<S, X> v) { return v.apply(s); }
          public X put(S n, Same<Void, X> v) { s = n; return v.apply(null); }
        });
      }
    }, body);
  }


  public static <S> Program<S> get(Key<State<S>> t) { return e -> e.perform(new State.Get<S>(t)); }

  public static <S> Program<Void> put(Key<State<S>> t, S s) { return e -> e.perform(new State.Put<>(t, s)); }

  public static <S> Program<Void> modify(Key<State<S>> t, Fn<S, S> f) {
    return e -> e.flatMap(e.perform(new State.Get<S>(t)), s -> e.perform(new State.Put<>(t, f.apply(s))));
  }

  // -- Reader

  /** READER: the value, answered in place */
  public static <R, A> Program<A> reader(Key<Reader<R>> t, R value, Program<A> body) {
    return e -> e.handle(new Answering<A, A>() {
      public Key<?> key() { return t; }
      public <X> X value(Op<X> op) {
        return Reader.narrow(t, op).fold(new Reader.Cases<R, X, X>() { public X ask(Same<R, X> v) { return v.apply(value); } });
      }
    }, body);
  }


  public static <R> Program<R> ask(Key<Reader<R>> t) { return e -> e.perform(new Reader.Ask<R>(t)); }

  /** the body, an ask in it answered by the reader outside, through {@code f}: an interceptor — its clause asks outward */
  public static <R, A> Program<A> local(Key<Reader<R>> t, Fn<R, R> f, Program<A> body) {
    return e -> e.handle(new General<A, A>() {
      public Key<?> key() { return t; }
      public Prog<A> ret(A a, Effects e2) { return e2.pure(a); }
      public <X> Prog<A> apply(Op<X> op, Fn<X, Prog<A>> k, Effects e2) {
        return Reader.narrow(t, op).fold(new Reader.Cases<R, X, Prog<A>>() {
          public Prog<A> ask(Same<R, X> v) { return e2.flatMap(e2.perform(new Reader.Ask<R>(t)), r -> k.apply(v.apply(f.apply(r)))); }
        });
      }
    }, body);
  }

  // -- Writer

  /** WRITER: the values told, in order, with the body's */
  public static <W, A> Program<Logged<W, A>> writer(Key<Writer<W>> t, Program<A> body) {
    return e -> e.handle(new Collect<W, A>(t, null), body);
  }

  /** the values told — a writer's, or an emitter's — collected in order with the body's: one of the two keys */
  private static final class Collect<W, A> implements General<A, Logged<W, A>>, Returning<A, Logged<W, A>> {
    private final Key<Writer<W>> writer;
    private final Key<Emit<W>> emit;
    Collect(Key<Writer<W>> writer, Key<Emit<W>> emit) { this.writer = writer; this.emit = emit; }
    public Key<?> key() { return writer != null ? writer : emit; }
    public Logged<W, A> answer(A a) { return Logged.of(a); }
    public Prog<Logged<W, A>> ret(A a, Effects e) { return e.pure(answer(a)); }
    public <X> Prog<Logged<W, A>> apply(Op<X> op, Fn<X, Prog<Logged<W, A>>> k, Effects e) {
      if (writer != null) {
        return Writer.narrow(writer, op).fold(new Writer.Cases<W, X, Prog<Logged<W, A>>>() {
          public Prog<Logged<W, A>> tell(W w, Same<Void, X> v) { return e.map(k.apply(v.apply(null)), l -> l.told(w)); }
        });
      }
      return Emit.narrow(emit, op).fold(new Emit.Cases<W, X, Prog<Logged<W, A>>>() {
        public Prog<Logged<W, A>> send(W w, Same<Void, X> v) { return e.map(k.apply(v.apply(null)), l -> l.told(w)); }
      });
    }
  }

  public static <W> Program<Void> tell(Key<Writer<W>> t, W w) { return e -> e.perform(new Writer.Tell<>(t, w)); }

  // -- Throws

  /** THROWS: the body's value, or the error it raised — the body ended there, its finalizers run */
  public static <E, A> Program<Result<E, A>> throwsOf(Key<Throws<E>> t, Program<A> body) {
    return e -> e.handle(new AbortingReturning<A, Result<E, A>>() {
      public Key<?> key() { return t; }
      public Result<E, A> answer(A a) { return new Ok<>(a); }
      public <X> Result<E, A> abort(Op<X> op) {
        return Throws.narrow(t, op).fold(new Throws.Cases<E, X, Result<E, A>>() { public Result<E, A> raise(E err) { return new Err<>(err); } });
      }
    }, body);
  }

  private interface AbortingReturning<A, R> extends Aborting<A, R>, Returning<A, R> {}


  public static <E, A> Program<A> raise(Key<Throws<E>> t, E error) { return e -> e.perform(new Throws.Raise<E, A>(t, error)); }

  /** the body, a raise in it answered by {@code h} — run outside the handler, so a raise in {@code h} goes outward */
  public static <E, A> Program<A> handleWith(Key<Throws<E>> t, Program<A> body, Fn<E, Program<A>> h) {
    return e -> e.handle(new General<A, A>() {
      public Key<?> key() { return t; }
      public Prog<A> ret(A a, Effects e2) { return e2.pure(a); }
      public <X> Prog<A> apply(Op<X> op, Fn<X, Prog<A>> k, Effects e2) {
        return Throws.narrow(t, op).fold(new Throws.Cases<E, X, Prog<A>>() { public Prog<A> raise(E err) { return h.apply(err).in(e2); } });
      }
    }, body);
  }

  // -- Choose

  /** CHOOSE: every value the body comes to, a path a choice */
  public static <A> Program<List<A>> all(Program<A> body) {
    return e -> e.handle(new General<A, List<A>>() {
      public Key<?> key() { return Choose.KEY; }
      public Prog<List<A>> ret(A a, Effects e2) { return e2.pure(List.of(a)); }
      public <X> Prog<List<A>> apply(Op<X> op, Fn<X, Prog<List<A>>> k, Effects e2) {
        return Choose.narrow(Choose.KEY, op).fold(new Choose.Cases<X, Prog<List<A>>>() {
          public Prog<List<A>> among(List<X> xs) { return from(xs, 0, new ArrayList<>(), k, e2); }
        });
      }
      private <X> Prog<List<A>> from(List<X> xs, int i, List<A> acc, Fn<X, Prog<List<A>>> k, Effects e2) {
        if (i == xs.size()) return e2.pure(Collections.unmodifiableList(acc));
        return e2.flatMap(k.apply(xs.get(i)), r -> {
          List<A> next = new ArrayList<>(acc);
          next.addAll(r);
          return from(xs, i + 1, next, k, e2);
        });
      }
    }, body);
  }

  /** the first value the body comes to, its choices tried in order, the rest not tried */
  public static <A> Program<Optional<A>> first(Program<A> body) {
    return e -> e.handle(new General<A, Optional<A>>() {
      public Key<?> key() { return Choose.KEY; }
      public Prog<Optional<A>> ret(A a, Effects e2) { return e2.pure(Optional.of(a)); }
      public <X> Prog<Optional<A>> apply(Op<X> op, Fn<X, Prog<Optional<A>>> k, Effects e2) {
        return Choose.narrow(Choose.KEY, op).fold(new Choose.Cases<X, Prog<Optional<A>>>() {
          public Prog<Optional<A>> among(List<X> xs) { return from(xs, 0, k, e2); }
        });
      }
      private <X> Prog<Optional<A>> from(List<X> xs, int i, Fn<X, Prog<Optional<A>>> k, Effects e2) {
        if (i == xs.size()) return e2.pure(Optional.empty());
        return e2.flatMap(k.apply(xs.get(i)), r -> r.isPresent() ? e2.pure(r) : from(xs, i + 1, k, e2));
      }
    }, body);
  }


  public static <X> Program<X> among(List<X> xs) { return e -> e.perform(new Choose.Among<>(xs)); }

  // -- Emit

  /** EMIT, each value given to {@code f} in place */
  public static <W, A> Program<A> forEach(Key<Emit<W>> t, Program<A> body, Fn<W, Void> f) {
    return e -> e.handle(new Answering<A, A>() {
      public Key<?> key() { return t; }
      public <X> X value(Op<X> op) {
        return Emit.narrow(t, op).fold(new Emit.Cases<W, X, X>() { public X send(W w, Same<Void, X> v) { return v.apply(f.apply(w)); } });
      }
    }, body);
  }

  /** EMIT, the values collected in order with the body's */
  public static <W, A> Program<Logged<W, A>> collect(Key<Emit<W>> t, Program<A> body) {
    return e -> e.handle(new Collect<W, A>(null, t), body);
  }

  /** EMIT AS A GENERATOR: its first position — a value and the rest, pulled when wanted, or the end */
  public static <W, A> Program<Gen<W, A>> generate(Key<Emit<W>> t, Program<A> body) {
    return e -> e.handle(new General<A, Gen<W, A>>() {
      public Key<?> key() { return t; }
      public Prog<Gen<W, A>> ret(A a, Effects e2) { return e2.pure(new Results.End<>(a)); }
      public <X> Prog<Gen<W, A>> apply(Op<X> op, Fn<X, Prog<Gen<W, A>>> k, Effects e2) {
        return Emit.narrow(t, op).fold(new Emit.Cases<W, X, Prog<Gen<W, A>>>() {
          public Prog<Gen<W, A>> send(W w, Same<Void, X> v) {
            Program<Gen<W, A>> rest = e3 -> k.apply(v.apply(null));
            return e2.pure(new Results.Next<>(w, rest));
          }
        });
      }
    }, body);
  }


  public static <W> Program<Void> send(Key<Emit<W>> t, W w) { return e -> e.perform(new Emit.Send<>(t, w)); }

  // -- effects from effects

  /** ONE'S EFFECT ON ANOTHER: each operation of {@code t} a program {@code f(op)} outside the handler, the rest of the
   * body resumed with its value — an audit to a writer, a ticket on a state */
  public static <A> Program<A> translate(Key<?> t, Translation f, Program<A> body) {
    return e -> e.handle(translating(t, f), body);
  }

  /** AN INTERCEPTOR of {@code t}, handled outside: each operation of the body a program {@code f(op)}, whose own
   * operations of {@code t} go to the handler outside — what {@code t} does in the body changed, not its effects */
  public static <A> Program<A> intercept(Key<?> t, Translation f, Program<A> body) {
    return e -> e.intercept(translating(t, f), body);
  }

  /** an operation's translation: a program of its value — a generic method, so an anonymous class, as Java's lambdas
   * are not generic */
  public interface Translation {
    <X> Program<X> apply(Op<X> op);
  }

  private static <A> Translating<A, A> translating(Key<?> t, Translation f) {
    return new Translating<A, A>() {
      public Key<?> key() { return t; }
      public <X> Program<X> translate(Op<X> op) { return f.apply(op); }
    };
  }

  // -- finalizers

  /** {@code acquire}, then {@code use} of it, then {@code release} of it — after a value, a failure or a
   * cancellation alike; the acquiring and the registering of the release one region cancellation waits for */
  public static <R, A> Program<A> bracket(Program<R> acquire, Fn<R, Program<A>> use, Fn<R, Program<Void>> release) {
    return e -> e.flatMap(e.uncancelable(acquire), r -> e.ensuring(use.apply(r), release.apply(r)));
  }
}
