package okay.kernel.std;

import okay.kernel.Fn;
import okay.kernel.Key;
import okay.kernel.Op;
import okay.kernel.Program;
import okay.kernel.Type;

/** CONTROL ACROSS IMPLEMENTATIONS: a {@code shift0} to a delimiter of another implementation, as an operation — the
 * delimiter's identity a fresh type, its handler the delimiter. Inside an implementation control is its own
 * ({@code Effects.shift0}, {@code dollar}) */
public final class Control {
  private Control() {}

  /** a delimiter's key: one a run of it */
  public static Key<Control> delimiter() { return Key.fresh(); }

  /**
   * AN OPERATION OF THIS EFFECT, AS ONE OF ITS OPERATIONS — what the handler of {@code key} is given: checked to be of
   * {@code key} (the same key, by identity) and one of {@code Ops}. Not, a failure
   */
  @SuppressWarnings("unchecked")
  public static <X> Ops<X> narrow(Key<Control> key, Op<X> op) {
    if (key.owns(op) && op instanceof Control.Ops<?>) return (Ops<X>) op;
    throw new IllegalArgumentException("not an operation of " + key + ": " + op);
  }

  /** its operations: the kernel's records below, or an implementation's own — implementing it, its {@code fold}
   * calling its own case (the TCK checks it) */
  public interface Ops<X> extends Op<X> {
    <R> R fold(Cases<X, R> c);
  }

  public interface Cases<X, R> {
    R capture(Fn<Fn<X, Program<Object>>, Program<Object>> body);
  }

  /** the rest up to the delimiter {@code effect}, the delimiter included, as {@code k}; {@code body} runs in its place */
  public record Capture<X>(Key<Control> key, Fn<Fn<X, Program<Object>>, Program<Object>> body) implements Ops<X> {
    public <R> R fold(Cases<X, R> c) { return c.capture(body); }
  }
}
