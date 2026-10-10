package okay.kernel;

/** AN OPERATION of an effect, as data, its value {@code X} */
public interface Op<X> {
  /** the key of the effect it is of: its handler is found by it */
  Key<?> key();

  /** the effect's exact identity */
  default Type effect() { return key().type(); }

  /** the type of its value, if known: checked at the border in a debug mode; {@code null} if not given */
  default Type value() { return null; }
}
