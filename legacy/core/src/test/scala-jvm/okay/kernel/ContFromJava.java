package okay.kernel;

import okay.Cont;
import okay.Cont;

/** SHIFT AND RESET WITH THE ANSWER TYPE MODIFIED, FROM JAVA: checked by Java's compiler */
public final class ContFromJava {
  private ContFromJava() {}

  /** reset(shift(k -> "got " + k(5)) + 1): the body's answer an Integer, the reset's a String — "got 6" */
  public static String moved(Monad m) {
    // Scala's Cont.Flat[Integer, String, Integer]: in Java its whole type
    Cont<Cont<Integer, Integer, Integer>, Cont<String, String, String>, Integer> body =
        Cont.<Integer, String, Integer>shift(k -> Cont.pure("got " + Cont.eval(k.apply(5), m))).map(x -> x + 1);
    return Cont.eval(Cont.<String, Integer, String>reset(body), m);
  }

  /** $ moving the answer: the body an Integer, its return a String, the clause the String's length — 4 */
  public static Integer dollar(Monad m) {
    Cont<Cont<String, String, String>, Cont<Integer, Integer, Integer>, Integer> body =
        Cont.<String, String, String, Cont<Integer, Integer, Integer>, Integer>shift0(
            k -> Cont.<Integer, Integer>pure(Cont.value(k.apply(7), m).length())).map(x -> x * 6);
    Cont<Integer, Integer, Integer> p = Cont.dollar(a -> Cont.<String, String>pure("n=" + a), body);
    return Cont.value(p, m);
  }

  /** short: the answer's type given once — 12 */
  public static Integer short0(Monad m) {
    Cont.At<Integer> d = Cont.at();
    return Cont.value(d.reset0(d.<Integer>shift0(k -> k.apply(10).flatMap(x -> k.apply(x))).map(x -> x + 1)), m);
  }

  /** short, shift: the answer moved, Integer to String — "got 6" */
  public static String shortMoved(Monad m) {
    Cont.At<Integer> d = Cont.at();
    return Cont.eval(d.reset(d.<Integer, String>shiftTo(k -> Cont.pure("got " + Cont.eval(k.apply(5), m))).map(x -> x + 1)), m);
  }

  /** the full shift0, no type written at the call: Java's inference from the variable's type — 12 */
  public static Integer byTarget(Monad m) {
    Cont<Cont<Integer, Integer, Integer>, Cont<Integer, Integer, Integer>, Integer> s =
        Cont.shift0(k -> k.apply(10).flatMap(x -> k.apply(x)));
    return Cont.value(Cont.reset0(s.map(x -> x + 1)), m);
  }

  /** shift0's: reset0(1 + shift0(k -> k(k(10)))) — 12 */
  public static Integer shift0(Monad m) {
    Cont<Integer, Integer, Integer> p = Cont.reset0(
        Cont.<Integer, Integer, Integer, Cont<Integer, Integer, Integer>, Integer>shift0(
            k -> k.apply(10).flatMap(x -> k.apply(x))).map(x -> x + 1));
    return Cont.value(p, m);
  }
}
