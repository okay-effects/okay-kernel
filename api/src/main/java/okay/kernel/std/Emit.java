package okay.kernel.std;

import okay.kernel.Op;
import okay.kernel.Same;
import okay.kernel.Type;

/** EMIT: values sent out one by one — a stream's source; its consumer the handler */
public final class Emit {
  private Emit() {}

  public static Type of(Type w) { return Type.of(Emit.class, w); }

  /**
   * AN OPERATION OF THIS EFFECT, AS ONE OF ITS OPERATIONS — what a handler found by the effect's identity is given.
   * The one trusted step of the vocabulary: an operation of the effect {@code Emit...} is one of {@code Ops}, its
   * type arguments the effect's (its {@code Type} says so). Checked as far as the JVM can: not one of them, a failure
   */
  @SuppressWarnings("unchecked")
  public static <W, X> Ops<W, X> narrow(okay.kernel.Op<X> op) {
    if (op instanceof Emit.Ops<?, ?>) return (Ops<W, X>) op;
    throw new IllegalArgumentException("not an operation of Emit: " + op);
  }

  /** its operations: the kernel's records below, or an implementation's own — implementing it, its {@code fold}
   * calling its own case (the TCK checks it) */
  public interface Ops<W, X> extends Op<X> {
    <R> R fold(Cases<W, X, R> c);
  }

  public interface Cases<W, X, R> {
    R send(W w, Same<Void, X> value);
  }

  public record Send<W>(Type effect, W w) implements Ops<W, Void> {
    public <R> R fold(Cases<W, Void, R> c) { return c.send(w, new Same.Refl<>()); }
  }
}
