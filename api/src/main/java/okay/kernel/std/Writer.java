package okay.kernel.std;

import okay.kernel.Key;
import okay.kernel.Op;
import okay.kernel.Same;
import okay.kernel.Type;

/** WRITER: values told, in order — {@code Writer<W>} the effect as a type, the phantom of its key */
public final class Writer<W> {
  private Writer() {}

  /** the effect's exact identity, over {@code w} */
  public static Type of(Type w) { return Type.of(Writer.class, w); }

  /** the effect's key over {@code w}, the type of {@code W}: one for all who ask */
  public static <W> Key<Writer<W>> key(Type w) { return Key.of(of(w)); }

  /**
   * AN OPERATION OF THIS EFFECT, AS ONE OF ITS OPERATIONS — what the handler of {@code key} is given: checked to be of
   * {@code key} (the same key, by identity) and one of {@code Ops}, so its types are the key's. Not, a failure
   */
  @SuppressWarnings("unchecked")
  public static <W, X> Ops<W, X> narrow(Key<Writer<W>> key, Op<X> op) {
    if (key.owns(op) && op instanceof Writer.Ops<?, ?>) return (Ops<W, X>) op;
    throw new IllegalArgumentException("not an operation of " + key + ": " + op);
  }

  /** its operations: the kernel's records below, or an implementation's own — implementing it, its {@code fold}
   * calling its own case (the TCK checks it) */
  public interface Ops<W, X> extends Op<X> {
    <R> R fold(Cases<W, X, R> c);
  }

  public interface Cases<W, X, R> {
    R tell(W w, Same<Void, X> value);
  }

  public record Tell<W>(Key<Writer<W>> key, W w) implements Ops<W, Void> {
    public <R> R fold(Cases<W, Void, R> c) { return c.tell(w, new Same.Refl<>()); }
  }
}
