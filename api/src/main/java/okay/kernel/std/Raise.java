package okay.kernel.std;

import okay.kernel.Effect;
import okay.kernel.Op;

/** a failure: the rest dropped — of any value, none ever given */
public record Raise<E, A>(E error, Effect effect) implements Op<A> {}
