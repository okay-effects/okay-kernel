package okay.jkernel.lexical;

import okay.kernel.Effect;
import okay.kernel.std.Get;
import okay.kernel.std.Put;
import okay.kernel.std.Raise;
import okay.kernel.std.Tell;

/** the standard capabilities: the API's operations, of the handler's fresh effect */
final class Caps {
  private Caps() {}

  record WriterCap<M, W>(Effect e) implements Writer<M, W> {
    public Prog<M, Void> tell(W w) { return Prog.of(m -> m.perform(new Tell<>(w, e))); }
  }

  record StateCap<M, S>(Effect e) implements State<M, S> {
    public Prog<M, S> get() { return Prog.of(m -> m.perform(new Get<S>(e))); }
    public Prog<M, Void> put(S s) { return Prog.of(m -> m.perform(new Put<>(s, e))); }
  }

  record ThrowsCap<M, E>(Effect e) implements Throws<M, E> {
    public <X> Prog<M, X> raise(E err) { return Prog.of(m -> m.perform(new Raise<E, X>(err, e))); }
  }
}
