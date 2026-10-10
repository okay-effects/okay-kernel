package okay.kernel.std;

import okay.kernel.Op;
import okay.kernel.Same;
import okay.kernel.Type;

/** WRITER: values told, in order */
public final class Writer {
  private Writer() {}

  public static Type of(Type w) { return Type.of(Writer.class, w); }

  /**
   * AN OPERATION OF THIS EFFECT, AS ONE OF ITS OPERATIONS — what a handler found by the effect's identity is given.
   * The one trusted step of the vocabulary: an operation of the effect {@code Writer...} is one of {@code Ops}, its
   * type arguments the effect's (its {@code Type} says so). Checked as far as the JVM can: not one of them, a failure
   */
  @SuppressWarnings("unchecked")
  public static <W, X> Ops<W, X> narrow(okay.kernel.Op<X> op) {
    if (op instanceof Writer.Ops<?, ?>) return (Ops<W, X>) op;
    throw new IllegalArgumentException("not an operation of Writer: " + op);
  }

  public sealed interface Ops<W, X> extends Op<X> permits Tell {
    <R> R fold(Cases<W, X, R> c);
  }

  public interface Cases<W, X, R> {
    R tell(W w, Same<Void, X> value);
  }

  public record Tell<W>(Type effect, W w) implements Ops<W, Void> {
    public <R> R fold(Cases<W, Void, R> c) { return c.tell(w, new Same.Refl<>()); }
  }
}
