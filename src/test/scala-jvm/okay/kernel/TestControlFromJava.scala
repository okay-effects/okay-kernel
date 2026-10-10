package okay.kernel

import okay.lexical.Prog

/** Java's shift and reset, on every monad */
abstract class TestControlFromJava(m: Monad) extends munit.FunSuite:
  test("1 + reset(2 * shift(k -> k(k(10)))) is 41") { assertEquals(Prog.run(ControlFromJava.twice(), m): Any, 41: Any) }
  test("a shift to the outer reset, past the inner: 1001") { assertEquals(Prog.run(ControlFromJava.past(), m): Any, 1001: Any) }
  test("multi-shot: 1, 10, 2, 20") { assertEquals(Prog.run(ControlFromJava.both(), m): Any, java.util.List.of(1, 10, 2, 20): Any) }
  test("shift0's clause outside its reset: -1, both resets dropped") { assertEquals(Prog.run(ControlFromJava.outside(), m): Any, -1: Any) }

class TestControlFromJavaSteps extends TestControlFromJava(Steps)
class TestControlFromJavaRef extends TestControlFromJava(Ref)
