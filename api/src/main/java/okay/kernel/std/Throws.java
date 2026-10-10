package okay.kernel.std;

import okay.kernel.Key;
import okay.kernel.Op;
import okay.kernel.Same;
import okay.kernel.Type;

/** THROWS: a typed error — a raise ends the body (its handler's shape {@code Aborting}); a failure is not one of
 * these, it is a step's ({@code Failed}) — {@code Throws<E>} the effect as a type, the phantom of its key */
public final class Throws<E> {
  private Throws() {}

  /** the effect's exact identity, over {@code e} */
  public static Type of(Type e) { return Type.of(Throws.class, e); }

  /** the effect's key over {@code e}, the type of {@code E}: one for all who ask */
  public static <E> Key<Throws<E>> key(Type e) { return Key.of(of(e)); }

  /**
   * AN OPERATION OF THIS EFFECT, AS ONE OF ITS OPERATIONS — what the handler of {@code key} is given: checked to be of
   * {@code key} (the same key, by identity) and one of {@code Ops}, so its types are the key's. Not, a failure
   */
  @SuppressWarnings("unchecked")
  public static <E, X> Ops<E, X> narrow(Key<Throws<E>> key, Op<X> op) {
    if (key.owns(op) && op instanceof Throws.Ops<?, ?>) return (Ops<E, X>) op;
    throw new IllegalArgumentException("not an operation of " + key + ": " + op);
  }

  /** its operations: the kernel's records below, or an implementation's own — implementing it, its {@code fold}
   * calling its own case (the TCK checks it) */
  public interface Ops<E, X> extends Op<X> {
    <R> R fold(Cases<E, X, R> c);
  }

  /** a raise has no value: no proof */
  public interface Cases<E, X, R> {
    R raise(E error);
  }

  public record Raise<E, X>(Key<Throws<E>> key, E error) implements Ops<E, X> {
    public <R> R fold(Cases<E, X, R> c) { return c.raise(error); }
  }
}
