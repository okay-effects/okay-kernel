package okay.jkernel.lexical;

/** a log's capability, at level {@code L} */
public interface Writer<L, W> {
  Prog<L, Void> tell(W w);

  @SuppressWarnings("unchecked")
  default <M extends L> Writer<M, W> at() {
    return (Writer<M, W>) this;
  }
}
