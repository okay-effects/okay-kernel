package okay.kernel.std;

import okay.kernel.Op;
import okay.kernel.Same;
import okay.kernel.Type;

/** STATE: a cell, read and written */
public final class State {
  private State() {}

  /** the state of {@code s} */
  public static Type of(Type s) { return Type.of(State.class, s); }

  public sealed interface Ops<S, X> extends Op<X> permits Get, Put {
    <R> R fold(Cases<S, X, R> c);
  }

  public interface Cases<S, X, R> {
    R get(Same<S, X> value);

    R put(S s, Same<Void, X> value);
  }

  public record Get<S>(Type effect) implements Ops<S, S> {
    public <R> R fold(Cases<S, S, R> c) { return c.get(new Same.Refl<>()); }
  }

  public record Put<S>(Type effect, S s) implements Ops<S, Void> {
    public <R> R fold(Cases<S, Void, R> c) { return c.put(s, new Same.Refl<>()); }
  }
}
