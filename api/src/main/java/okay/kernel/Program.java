package okay.kernel;

/** A PROGRAM WRITTEN ONCE: built in any implementation's {@link Effects} */
@FunctionalInterface
public interface Program<A> {
  Prog<A> in(Effects e);
}
