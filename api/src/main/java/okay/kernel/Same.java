package okay.kernel;

/**
 * {@code A} AND {@code B} THE SAME TYPE: its only proof {@link Refl}, whose {@code apply} is the identity — so a value
 * of {@code A} is one of {@code B}, with no cast. An operation's case gives it, a handler's clause uses it: the value of
 * the operation, typed by the operation (an effect's {@code Cases})
 */
public sealed interface Same<A, B> permits Same.Refl {
  B apply(A a);

  /** the proof: {@code A} is {@code A} */
  record Refl<A>() implements Same<A, A> {
    public A apply(A a) { return a; }
  }

}
