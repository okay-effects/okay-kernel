package okay.kernel.std;

import okay.kernel.Op;
import okay.kernel.Same;
import okay.kernel.Type;

/** STATE: a cell, read and written */
public final class State {
  private State() {}

  /** the state of {@code s} */
  public static Type of(Type s) { return Type.of(State.class, s); }

  /**
   * AN OPERATION OF THIS EFFECT, AS ONE OF ITS OPERATIONS — what a handler found by the effect's identity is given.
   * The one trusted step of the vocabulary: an operation of the effect {@code State...} is one of {@code Ops}, its
   * type arguments the effect's (its {@code Type} says so). Checked as far as the JVM can: not one of them, a failure
   */
  @SuppressWarnings("unchecked")
  public static <S, X> Ops<S, X> narrow(okay.kernel.Op<X> op) {
    if (op instanceof State.Ops<?, ?>) return (Ops<S, X>) op;
    throw new IllegalArgumentException("not an operation of State: " + op);
  }

  /** its operations: the kernel's records below, or an implementation's own — implementing it, its {@code fold}
   * calling its own case (the TCK checks it) */
  public interface Ops<S, X> extends Op<X> {
    <R> R fold(Cases<S, X, R> c);
  }

  public interface Cases<S, X, R> {
    R get(Same<S, X> value);

    R put(S s, Same<Void, X> value);
  }

  public record Get<S>(Type effect) implements Ops<S, S> {
    public <R> R fold(Cases<S, S, R> c) { return c.get(new Same.Refl<>()); }
  }

  public record Put<S>(Type effect, S s) implements Ops<S, Void> {
    public <R> R fold(Cases<S, Void, R> c) { return c.put(s, new Same.Refl<>()); }
  }
}
