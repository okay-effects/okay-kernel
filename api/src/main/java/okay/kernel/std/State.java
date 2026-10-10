package okay.kernel.std;

import okay.kernel.Key;
import okay.kernel.Op;
import okay.kernel.Same;
import okay.kernel.Type;

/** STATE: a cell, read and written — {@code State<S>} the effect as a type, the phantom of its key */
public final class State<S> {
  private State() {}

  /** the effect's exact identity, over {@code s} */
  public static Type of(Type s) { return Type.of(State.class, s); }

  /** the effect's key over {@code s}, the type of {@code S}: one for all who ask */
  public static <S> Key<State<S>> key(Type s) { return Key.of(of(s)); }

  /**
   * AN OPERATION OF THIS EFFECT, AS ONE OF ITS OPERATIONS — what the handler of {@code key} is given: checked to be of
   * {@code key} (the same key, by identity) and one of {@code Ops}, so its types are the key's. Not, a failure
   */
  @SuppressWarnings("unchecked")
  public static <S, X> Ops<S, X> narrow(Key<State<S>> key, Op<X> op) {
    if (key.owns(op) && op instanceof State.Ops<?, ?>) return (Ops<S, X>) op;
    throw new IllegalArgumentException("not an operation of " + key + ": " + op);
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

  public record Get<S>(Key<State<S>> key) implements Ops<S, S> {
    public <R> R fold(Cases<S, S, R> c) { return c.get(new Same.Refl<>()); }
  }

  public record Put<S>(Key<State<S>> key, S s) implements Ops<S, Void> {
    public <R> R fold(Cases<S, Void, R> c) { return c.put(s, new Same.Refl<>()); }
  }
}
