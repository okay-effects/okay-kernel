package okay.kernel.std;

import java.util.List;
import okay.kernel.Effect;
import okay.kernel.Op;

/** a choice among values: the rest run for each (multi-shot) */
public record Among<A>(List<A> as) implements Op<A> {
  public Effect effect() { return Effects.CHOOSE; }
}
