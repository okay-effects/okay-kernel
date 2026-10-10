package okay.kernel.std;

import okay.kernel.Effect;
import okay.kernel.Fn;
import okay.kernel.Op;
import okay.kernel.Prog;
import okay.kernel.Program;

/** shift to the delimiter of {@code effect}: as {@link Shift0}, its clause under the delimiter again (Danvy–Filinski) */
public record Shift<A, R>(Effect effect, Fn<Fn<A, Prog<R>>, Program<R>> f) implements Op<A> {}
