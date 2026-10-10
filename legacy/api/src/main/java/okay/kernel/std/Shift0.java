package okay.kernel.std;

import okay.kernel.Effect;
import okay.kernel.Fn;
import okay.kernel.Op;
import okay.kernel.Prog;
import okay.kernel.Program;

/** shift0 to the delimiter of {@code effect}: the rest up to it, the delimiter included, to {@code f}, run in its
 * place, outside it (Materzok–Biernacki) */
public record Shift0<A, R>(Effect effect, Fn<Fn<A, Prog<R>>, Program<R>> f) implements Op<A> {}
