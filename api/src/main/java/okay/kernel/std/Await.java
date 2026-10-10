package okay.kernel.std;

import okay.kernel.Effect;
import okay.kernel.Op;
import okay.kernel.Sink;

/** a callback's value awaited: {@code register} given the callback, called now or later, from any thread */
public record Await<A>(Sink<Callback<A>> register) implements Op<A> {
  public Effect effect() { return Effects.ASYNC; }
}
