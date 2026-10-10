package okay.jkernel;

import okay.kernel.Monad;

/** the reference monad, for the tests of other packages */
public final class RefAccess {
  private RefAccess() {}

  public static Monad ref() { return Ref.INSTANCE; }
}
