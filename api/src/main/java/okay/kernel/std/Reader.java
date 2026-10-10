package okay.kernel.std;

import okay.kernel.Op;
import okay.kernel.Same;
import okay.kernel.Type;

/** READER: a value given from outside */
public final class Reader {
  private Reader() {}

  public static Type of(Type e) { return Type.of(Reader.class, e); }

  /**
   * AN OPERATION OF THIS EFFECT, AS ONE OF ITS OPERATIONS — what a handler found by the effect's identity is given.
   * The one trusted step of the vocabulary: an operation of the effect {@code Reader...} is one of {@code Ops}, its
   * type arguments the effect's (its {@code Type} says so). Checked as far as the JVM can: not one of them, a failure
   */
  @SuppressWarnings("unchecked")
  public static <E, X> Ops<E, X> narrow(okay.kernel.Op<X> op) {
    if (op instanceof Reader.Ops<?, ?>) return (Ops<E, X>) op;
    throw new IllegalArgumentException("not an operation of Reader: " + op);
  }

  public sealed interface Ops<E, X> extends Op<X> permits Ask {
    <R> R fold(Cases<E, X, R> c);
  }

  public interface Cases<E, X, R> {
    R ask(Same<E, X> value);
  }

  public record Ask<E>(Type effect) implements Ops<E, E> {
    public <R> R fold(Cases<E, E, R> c) { return c.ask(new Same.Refl<>()); }
  }
}
