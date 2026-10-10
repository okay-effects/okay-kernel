package okay.kernel.std;

import okay.kernel.Key;
import okay.kernel.Op;
import okay.kernel.Same;
import okay.kernel.Type;

/** READER: a value given from outside — {@code Reader<E>} the effect as a type, the phantom of its key */
public final class Reader<E> {
  private Reader() {}

  /** the effect's exact identity, over {@code e} */
  public static Type of(Type e) { return Type.of(Reader.class, e); }

  /** the effect's key over {@code e}, the type of {@code E}: one for all who ask */
  public static <E> Key<Reader<E>> key(Type e) { return Key.of(of(e)); }

  /**
   * AN OPERATION OF THIS EFFECT, AS ONE OF ITS OPERATIONS — what the handler of {@code key} is given: checked to be of
   * {@code key} (the same key, by identity) and one of {@code Ops}, so its types are the key's. Not, a failure
   */
  @SuppressWarnings("unchecked")
  public static <E, X> Ops<E, X> narrow(Key<Reader<E>> key, Op<X> op) {
    if (key.owns(op) && op instanceof Reader.Ops<?, ?>) return (Ops<E, X>) op;
    throw new IllegalArgumentException("not an operation of " + key + ": " + op);
  }

  /** its operations: the kernel's records below, or an implementation's own — implementing it, its {@code fold}
   * calling its own case (the TCK checks it) */
  public interface Ops<E, X> extends Op<X> {
    <R> R fold(Cases<E, X, R> c);
  }

  public interface Cases<E, X, R> {
    R ask(Same<E, X> value);
  }

  public record Ask<E>(Key<Reader<E>> key) implements Ops<E, E> {
    public <R> R fold(Cases<E, E, R> c) { return c.ask(new Same.Refl<>()); }
  }
}
