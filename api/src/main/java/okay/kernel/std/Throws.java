package okay.kernel.std;

import okay.kernel.Op;
import okay.kernel.Type;

/** THROWS: a typed error — a raise ends the body (its handler's shape {@code Aborting}); a failure is not one of
 * these, it is a step's ({@code Failed}) */
public final class Throws {
  private Throws() {}

  public static Type of(Type e) { return Type.of(Throws.class, e); }

  /**
   * AN OPERATION OF THIS EFFECT, AS ONE OF ITS OPERATIONS — what a handler found by the effect's identity is given.
   * The one trusted step of the vocabulary: an operation of the effect {@code Throws...} is one of {@code Ops}, its
   * type arguments the effect's (its {@code Type} says so). Checked as far as the JVM can: not one of them, a failure
   */
  @SuppressWarnings("unchecked")
  public static <E, X> Ops<E, X> narrow(okay.kernel.Op<X> op) {
    if (op instanceof Throws.Ops<?, ?>) return (Ops<E, X>) op;
    throw new IllegalArgumentException("not an operation of Throws: " + op);
  }

  public sealed interface Ops<E, X> extends Op<X> permits Raise {
    <R> R fold(Cases<E, X, R> c);
  }

  /** a raise has no value: no proof */
  public interface Cases<E, X, R> {
    R raise(E error);
  }

  public record Raise<E, X>(Type effect, E error) implements Ops<E, X> {
    public <R> R fold(Cases<E, X, R> c) { return c.raise(error); }
  }
}
