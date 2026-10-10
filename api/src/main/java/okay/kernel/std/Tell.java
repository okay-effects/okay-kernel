package okay.kernel.std;

import okay.kernel.Effect;
import okay.kernel.Op;

/** a log: {@code w} in front of the rest's */
public record Tell<W>(W w, Effect effect) implements Op<Void> {}
