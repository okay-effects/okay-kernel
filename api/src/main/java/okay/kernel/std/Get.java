package okay.kernel.std;

import okay.kernel.Effect;
import okay.kernel.Op;

/** a state's, read: answered in place from the handler's cell */
public record Get<S>(Effect effect) implements Op<S> {}
