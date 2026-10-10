package okay.kernel;

/** AN OPERATION of an effect, as data, its value {@code X} */
public interface Op<X> {
  /** the effect it is of */
  Type effect();

  /** the type of its value, if known: checked at the border in a debug mode; {@code null} if not given */
  default Type value() { return null; }
}
