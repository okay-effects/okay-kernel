package okay.jkernel.lexical;

/** a handler's body: a generic method, its level {@code M} fresh — given the capability at {@code M}, which names it only */
public interface WriterBody<L, W, A> {
  <M extends L> Prog<M, A> in(Writer<M, W> w);
}
