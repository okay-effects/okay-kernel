package okay.kernel.std;

import okay.kernel.Fn;
import okay.kernel.Op;
import okay.kernel.Program;
import okay.kernel.Type;

/** CONTROL ACROSS IMPLEMENTATIONS: a {@code shift0} to a delimiter of another implementation, as an operation — the
 * delimiter's identity a fresh type, its handler the delimiter. Inside an implementation control is its own
 * ({@code Effects.shift0}, {@code dollar}) */
public final class Control {
  private Control() {}

  /** a delimiter's identity: one a run of it */
  public static Type delimiter() { return Type.fresh(); }

  public sealed interface Ops<X> extends Op<X> permits Capture {
    <R> R fold(Cases<X, R> c);
  }

  public interface Cases<X, R> {
    R capture(Fn<Fn<X, Program<Object>>, Program<Object>> body);
  }

  /** the rest up to the delimiter {@code effect}, the delimiter included, as {@code k}; {@code body} runs in its place */
  public record Capture<X>(Type effect, Fn<Fn<X, Program<Object>>, Program<Object>> body) implements Ops<X> {
    public <R> R fold(Cases<X, R> c) { return c.capture(body); }
  }
}
