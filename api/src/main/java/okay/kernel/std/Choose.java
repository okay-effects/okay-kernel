package okay.kernel.std;

import java.util.List;
import okay.kernel.Op;
import okay.kernel.Type;

/** CHOOSE: one of several values, multi-shot — none, a path that ends */
public final class Choose {
  private Choose() {}

  public static final Type TYPE = Type.of(Choose.class);

  /**
   * AN OPERATION OF THIS EFFECT, AS ONE OF ITS OPERATIONS — what a handler found by the effect's identity is given.
   * The one trusted step of the vocabulary: an operation of the effect {@code Choose...} is one of {@code Ops}, its
   * type arguments the effect's (its {@code Type} says so). Checked as far as the JVM can: not one of them, a failure
   */
  @SuppressWarnings("unchecked")
  public static <X> Ops<X> narrow(okay.kernel.Op<X> op) {
    if (op instanceof Choose.Ops<?>) return (Ops<X>) op;
    throw new IllegalArgumentException("not an operation of Choose: " + op);
  }

  /** its operations: the kernel's records below, or an implementation's own — implementing it, its {@code fold}
   * calling its own case (the TCK checks it) */
  public interface Ops<X> extends Op<X> {
    <R> R fold(Cases<X, R> c);
  }

  public interface Cases<X, R> {
    R among(List<X> xs);
  }

  public record Among<X>(Type effect, List<X> xs) implements Ops<X> {
    public Among(List<X> xs) { this(TYPE, xs); }
    public <R> R fold(Cases<X, R> c) { return c.among(xs); }
  }
}
