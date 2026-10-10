package okay.kernel;

/** AN OPERATION of an effect, as data, its value {@code X} */
public interface Op<X> {
  Effect effect();
}
