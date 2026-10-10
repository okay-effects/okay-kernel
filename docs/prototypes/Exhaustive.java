import java.util.function.Function;

/** the kernel's: X and Y the same type, its only proof refl — apply is the identity, typed, no cast */
sealed interface Same<A, B> permits Same.Refl {
  B apply(A a);
  record Refl<A>() implements Same<A, A> { public A apply(A a) { return a; } }
}

/** the kernel's: an operation of value X */
interface Op<X> {}

/** an effect: a sealed interface of records, and its CASES as a visitor — every case a method, each with the proof
 * of what the operation's value is: an implementation that misses one does not compile */
sealed interface State<S, X> extends Op<X> {
  interface Cases<S, X, R> {
    R get(Same<S, X> value);
    R put(S s, Same<Void, X> value);
  }
  <R> R match(Cases<S, X, R> c);

  record Get<S>() implements State<S, S> {
    public <R> R match(Cases<S, S, R> c) { return c.get(new Same.Refl<>()); }
  }
  record Put<S>(S s) implements State<S, Void> {
    public <R> R match(Cases<S, Void, R> c) { return c.put(s, new Same.Refl<>()); }
  }
}

public class Exh {
  static int s = 1;

  /** a clause: k typed by the operation's value, the value given through the proof — no cast */
  static <X, R> R clause(State<Integer, X> op, Function<X, R> k) {
    return op.match(new State.Cases<Integer, X, R>() {
      public R get(Same<Integer, X> v) { return k.apply(v.apply(s)); }
      public R put(Integer n, Same<Void, X> v) { s = n; return k.apply(v.apply(null)); }
    });
  }

  public static void main(String[] args) {
    System.out.println(clause(new State.Get<Integer>(), (Integer x) -> "got " + x));
    System.out.println(clause(new State.Put<Integer>(5), (Void u) -> "put"));
    System.out.println(clause(new State.Get<Integer>(), (Integer x) -> "got " + x));
  }
}
