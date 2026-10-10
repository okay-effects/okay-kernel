package okay.jkernel.lexical;

/** a reset's body: a generic method, its level {@code M} fresh — given the prompt, at {@code M} */
public interface ResetBody<L, R> {
  <M extends L> Prog<M, R> in(Prompt<L, M, R> p);
}
