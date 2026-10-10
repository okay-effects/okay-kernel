package okay.kernel.std;

import okay.kernel.Op;
import okay.kernel.Type;

/** THROWS: a typed error — a raise ends the body (its handler's shape {@code Aborting}); a failure is not one of
 * these, it is a step's ({@code Failed}) */
public final class Throws {
  private Throws() {}

  public static Type of(Type e) { return Type.of(Throws.class, e); }

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
