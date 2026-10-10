package okay.kernel.std;

import okay.kernel.Op;
import okay.kernel.Same;
import okay.kernel.Type;

/** READER: a value given from outside */
public final class Reader {
  private Reader() {}

  public static Type of(Type e) { return Type.of(Reader.class, e); }

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
