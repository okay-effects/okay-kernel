package okay.kernel;

import java.util.Set;

/** A PROGRAM OF ANY IMPLEMENTATION, HANDED OUT: started, it runs on its own machine and steps out where it needs what
 * it cannot answer */
public interface Computation<A> {
  Step<A> start(Answers host);

  /** the effects it may perform, exactly — its row at run time, checked at the border */
  Set<Type> effects();

  /** the type of its value, if known; {@code null} if not given */
  default Type value() { return null; }
}
