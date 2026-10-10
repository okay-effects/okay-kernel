package okay.kernel.std;

import okay.kernel.Op;
import okay.kernel.Same;
import okay.kernel.Type;

/** WRITER: values told, in order */
public final class Writer {
  private Writer() {}

  public static Type of(Type w) { return Type.of(Writer.class, w); }

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
