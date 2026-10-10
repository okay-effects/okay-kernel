package okay.jkernel.lexical;

/** a failure's capability, at level {@code L} */
public interface Throws<L, E> {
  <X> Prog<L, X> raise(E e);

  @SuppressWarnings("unchecked")
  default <M extends L> Throws<M, E> at() {
    return (Throws<M, E>) this;
  }
}
