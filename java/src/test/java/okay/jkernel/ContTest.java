package okay.jkernel;

import static org.junit.jupiter.api.Assertions.*;

import okay.kernel.Monad;
import org.junit.jupiter.api.Test;

/** SHIFT0/$ AND SHIFT/RESET, THE ANSWER TYPE MODIFIED: okay-kernel's {@code okay.Cont} suite from Java, here */
abstract class ContTest {
  final Monad m;

  ContTest(Monad m) { this.m = m; }

  /** reset(shift(k -> "got " + k(5)) + 1): the body's answer an Integer, the reset's a String */
  @Test void theAnswerMovedIntegerToString() {
    Cont<Cont<Integer, Integer, Integer>, Cont<String, String, String>, Integer> body =
        Cont.<Integer, String, Integer>shift(k -> Cont.pure("got " + Cont.eval(k.apply(5), m))).map(x -> x + 1);
    assertEquals("got 6", Cont.eval(Cont.<String, Integer, String>reset(body), m));
  }

  /** reset0(1 + shift0(k -> k(k(10)))): 12 */
  @Test void shift0Twice() {
    Cont<Integer, Integer, Integer> p = Cont.reset0(
        Cont.<Integer, Integer, Integer, Cont<Integer, Integer, Integer>, Integer>shift0(
            k -> k.apply(10).flatMap(x -> k.apply(x))).map(x -> x + 1));
    assertEquals(12, Cont.value(p, m));
  }

  /** $ moving the answer: the body an Integer, its return a String, the clause the String's length — 4 */
  @Test void dollarMovingTheAnswer() {
    Cont<Cont<String, String, String>, Cont<Integer, Integer, Integer>, Integer> body =
        Cont.<String, String, String, Cont<Integer, Integer, Integer>, Integer>shift0(
            k -> Cont.<Integer, Integer>pure(Cont.value(k.apply(7), m).length())).map(x -> x * 6);
    Cont<Integer, Integer, Integer> p = Cont.dollar(a -> Cont.<String, String>pure("n=" + a), body);
    assertEquals(4, Cont.value(p, m));
  }

  @Test void shortTheAnswersTypeOnce() {
    Cont.At<Integer> d = Cont.at();
    assertEquals(12, Cont.value(d.reset0(d.<Integer>shift0(k -> k.apply(10).flatMap(x -> k.apply(x))).map(x -> x + 1)), m));
    assertEquals("got 6", Cont.eval(d.reset(d.<Integer, String>shiftTo(k -> Cont.pure("got " + Cont.eval(k.apply(5), m))).map(x -> x + 1)), m));
  }

  @Test void theFullShift0ByTheVariablesType() {
    Cont<Cont<Integer, Integer, Integer>, Cont<Integer, Integer, Integer>, Integer> s =
        Cont.shift0(k -> k.apply(10).flatMap(x -> k.apply(x)));
    assertEquals(12, Cont.value(Cont.reset0(s.map(x -> x + 1)), m));
  }

  /** two shift0s to one delimiter, the second under each resumption of the first:
   * reset0(shift0(k -> k(k(1))) + shift0(k -> k(10))) — k(1) is 11, k(11) is 21 */
  @Test void twoShift0sOneDelimiter() {
    Cont.At<Integer> d = Cont.at();
    Cont<Integer, Integer, Integer> p = d.reset0(d.<Integer>shift0(k -> k.apply(1).flatMap(x -> k.apply(x)))
        .flatMap(a -> d.<Integer>shift0(k -> k.apply(10)).map(b -> a + b)));
    assertEquals(21, Cont.value(p, m));
  }
}

class ContStepsTest extends ContTest {
  ContStepsTest() { super(Monads.steps()); }
}

class ContRefTest extends ContTest {
  ContRefTest() { super(Ref.INSTANCE); }
}
