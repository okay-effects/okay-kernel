package okay.kernel;

import okay.Cont;
import okay.Cont0;

/** SHIFT AND RESET WITH THE ANSWER TYPE MODIFIED, FROM JAVA: checked by Java's compiler */
public final class ContFromJava {
  private ContFromJava() {}

  /** reset(shift(k -> "got " + k(5)) + 1): the body's answer an Integer, the reset's a String — "got 6" */
  public static String moved(Monad m) {
    Cont<Integer, String, Integer> body = Cont.<Integer, String, Integer>shift(
        k -> Cont.pure("got " + Cont.eval(k.apply(5), m))).map(x -> x + 1);
    return Cont.value(Cont.<String, Integer, String>reset(body), m);
  }

  /** shift0's: reset0(1 + shift0(k -> k(k(10)))) — 12 */
  public static Integer shift0(Monad m) {
    Cont0<Integer, Integer, Integer> p = Cont0.reset0(
        Cont0.<Integer, Integer, Integer, Cont0<Integer, Integer, Integer>, Integer>shift0(
            k -> k.apply(10).flatMap(x -> k.apply(x))).map(x -> x + 1));
    return Cont0.value(p, m);
  }
}
