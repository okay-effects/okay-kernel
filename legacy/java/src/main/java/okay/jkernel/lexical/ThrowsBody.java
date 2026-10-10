package okay.jkernel.lexical;

/** a handler's body: a generic method, its level {@code M} fresh — given the capability at {@code M}, which names it only */
public interface ThrowsBody<L, E, A> {
  <M extends L> Prog<M, A> in(Throws<M, E> t);
}
