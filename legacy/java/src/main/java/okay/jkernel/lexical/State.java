package okay.jkernel.lexical;

/** a state's capability, at level {@code L} */
public interface State<L, S> {
  Prog<L, S> get();

  Prog<L, Void> put(S s);

  @SuppressWarnings("unchecked")
  default <M extends L> State<M, S> at() {
    return (State<M, S>) this;
  }
}
