package okay.kernel.std;

import okay.kernel.Effect;
import okay.kernel.Op;

/** a state's, written */
public record Put<S>(S s, Effect effect) implements Op<Void> {}
