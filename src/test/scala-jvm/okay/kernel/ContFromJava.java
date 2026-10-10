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

  /** $ moving the answer: the body an Integer, its return a String, the clause the String's length — 4 */
  public static Integer dollar(Monad m) {
    Cont0<Cont0<String, String, String>, Cont0<Integer, Integer, Integer>, Integer> body =
        Cont0.<String, String, String, Cont0<Integer, Integer, Integer>, Integer>shift0(
            k -> Cont0.<Integer, Integer>pure(Cont0.value(k.apply(7), m).length())).map(x -> x * 6);
    Cont0<Integer, Integer, Integer> p = Cont0.dollar(a -> Cont0.<String, String>pure("n=" + a), body);
    return Cont0.value(p, m);
  }

  /** short: the answer's type given once — 12 */
  public static Integer short0(Monad m) {
    Cont0.At<Integer> d = Cont0.at();
    return Cont0.value(d.reset0(d.<Integer>shift0(k -> k.apply(10).flatMap(x -> k.apply(x))).map(x -> x + 1)), m);
  }

  /** short, shift: the answer moved, Integer to String — "got 6" */
  public static String shortMoved(Monad m) {
    Cont.At<Integer> d = Cont.at();
    return Cont.value(d.reset(d.<Integer, String>shiftTo(k -> Cont.pure("got " + Cont.eval(k.apply(5), m))).map(x -> x + 1)), m);
  }

  /** the full shift0, no type written at the call: Java's inference from the variable's type — 12 */
  public static Integer byTarget(Monad m) {
    Cont0<Cont0<Integer, Integer, Integer>, Cont0<Integer, Integer, Integer>, Integer> s =
        Cont0.shift0(k -> k.apply(10).flatMap(x -> k.apply(x)));
    return Cont0.value(Cont0.reset0(s.map(x -> x + 1)), m);
  }

  /** shift0's: reset0(1 + shift0(k -> k(k(10)))) — 12 */
  public static Integer shift0(Monad m) {
    Cont0<Integer, Integer, Integer> p = Cont0.reset0(
        Cont0.<Integer, Integer, Integer, Cont0<Integer, Integer, Integer>, Integer>shift0(
            k -> k.apply(10).flatMap(x -> k.apply(x))).map(x -> x + 1));
    return Cont0.value(p, m);
  }
}
