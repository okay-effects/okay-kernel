package okay.jkernel;

import okay.kernel.*;

/** the monads of this implementation */
public final class Monads {
  private Monads() {}

  /** the machine of steps */
  public static Monad steps() {
    return Steps.INSTANCE;
  }
}
