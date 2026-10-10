package okay.kernel.std;

import java.util.List;
import okay.kernel.Key;
import okay.kernel.Op;
import okay.kernel.Type;

/** CHOOSE: one of several values, multi-shot — none, a path that ends */
public final class Choose {
  private Choose() {}

  /** its key: one effect, of no parameter */
  public static final Key<Choose> KEY = Key.of(Type.of(Choose.class));

  /** its exact identity */
  public static final Type TYPE = KEY.type();

  /**
   * AN OPERATION OF THIS EFFECT, AS ONE OF ITS OPERATIONS — what the handler of {@code key} is given: checked to be of
   * {@code key} (the same key, by identity) and one of {@code Ops}. Not, a failure
   */
  @SuppressWarnings("unchecked")
  public static <X> Ops<X> narrow(Key<Choose> key, Op<X> op) {
    if (key.owns(op) && op instanceof Choose.Ops<?>) return (Ops<X>) op;
    throw new IllegalArgumentException("not an operation of " + key + ": " + op);
  }

  /** its operations: the kernel's records below, or an implementation's own — implementing it, its {@code fold}
   * calling its own case (the TCK checks it) */
  public interface Ops<X> extends Op<X> {
    <R> R fold(Cases<X, R> c);
  }

  public interface Cases<X, R> {
    R among(List<X> xs);
  }

  public record Among<X>(Key<Choose> key, List<X> xs) implements Ops<X> {
    public Among(List<X> xs) { this(KEY, xs); }
    public <R> R fold(Cases<X, R> c) { return c.among(xs); }
  }
}
