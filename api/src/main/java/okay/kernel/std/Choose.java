package okay.kernel.std;

import java.util.List;
import okay.kernel.Op;
import okay.kernel.Type;

/** CHOOSE: one of several values, multi-shot — none, a path that ends */
public final class Choose {
  private Choose() {}

  public static final Type TYPE = Type.of(Choose.class);

  public sealed interface Ops<X> extends Op<X> permits Among {
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
