package okay.kernel.std;

import okay.kernel.Op;
import okay.kernel.Same;
import okay.kernel.Type;

/** EMIT: values sent out one by one — a stream's source; its consumer the handler */
public final class Emit {
  private Emit() {}

  public static Type of(Type w) { return Type.of(Emit.class, w); }

  public sealed interface Ops<W, X> extends Op<X> permits Send {
    <R> R fold(Cases<W, X, R> c);
  }

  public interface Cases<W, X, R> {
    R send(W w, Same<Void, X> value);
  }

  public record Send<W>(Type effect, W w) implements Ops<W, Void> {
    public <R> R fold(Cases<W, Void, R> c) { return c.send(w, new Same.Refl<>()); }
  }
}
