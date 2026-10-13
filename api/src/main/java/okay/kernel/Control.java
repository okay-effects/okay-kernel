package okay.kernel;

/**
 * DELIMITED CONTROL, APART FROM {@code Effects}: the minimal pair, {@code shift0} and {@code $}. An implementation
 * that has it implements this beside {@code Effects}; code that needs it asks, {@link #of}. A handler is a delimiter:
 * {@code shift0} takes the rest up to the nearest one — a {@code $}'s or a handler's. The answer types are only
 * parameters here: the fronts check them.
 */
public interface Control {
  /** the rest up to the nearest delimiter, the delimiter included, as {@code k}; {@code body} runs outside it, in its
   * place */
  <A, I, O> Prog<A> shift0(Fn<Fn<A, Prog<I>>, Prog<O>> body);

  /** {@code body} under a fresh delimiter, its value given to {@code k}: {@code body $ k} */
  <A, I, O> Prog<O> dollar(Program<A> body, Fn<A, Prog<I>> k);

  /** the control of {@code e}, if it has it */
  static Control of(Effects e) {
    if (e instanceof Control c) return c;
    throw new UnsupportedOperationException(e + " has no delimited control");
  }
}
