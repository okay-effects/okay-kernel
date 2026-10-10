package okay.jkernel.lexical;

/** a handler's body: a generic method, its level {@code M} fresh — given the capability at {@code M}, which names it only */
public interface Body<L, A> {
  <M extends L> Prog<M, A> in(Cap<M> cap);
}
