package okay.kernel.std;

/** what a callback is given: the value, or the failure */
public interface Callback<A> {
  void ok(A a);
  void fail(Throwable e);
}
