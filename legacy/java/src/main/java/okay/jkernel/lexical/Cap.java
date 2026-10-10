package okay.jkernel.lexical;

import okay.kernel.Effect;
import okay.kernel.Fn;
import okay.kernel.Op;

/** one's own effect's capability: an operation of it, built for its effect */
public final class Cap<L> {
  private final Effect e;

  Cap(Effect e) {
    this.e = e;
  }

  public <X> Prog<L, X> perform(Fn<Effect, Op<X>> op) {
    return Prog.of(m -> m.perform(op.apply(e)));
  }

  @SuppressWarnings("unchecked")
  public <M extends L> Cap<M> at() {
    return (Cap<M>) this;
  }
}
