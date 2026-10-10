package okay.kernel

import okay.Cont

/** SHIFT AND RESET WITH THE ANSWER TYPE MODIFIED, on every monad */
abstract class TestContOn(m: Monad) extends munit.FunSuite:

  test("reset(1 + shift(k => k(k(10)))) is 12: the answer kept") {
    val p = Cont.reset[Int, Int, Int](Cont.shift[Int, Int, Int](k => k(10).flatMap(k)).map(_ + 1))
    assertEquals(Cont.value(p, m), 12)
  }

  test("the answer moved, Int to String: the body's answer an Int, the reset's a String") {
    val p = Cont.reset[String, Int, String](
      Cont.shift[Int, String, Int](k => Cont.pure("got " + Cont.eval(k(5), m))).map(_ + 1))
    assertEquals(Cont.value(p, m), "got 6")
  }

  test("Danvy's printf: the answer moved once a hole, the format's type its arguments'") {
    def int[T]: Cont[T, Int => T, String] =
      Cont.shift[T, Int => T, String](k => Cont.pure((n: Int) => Cont.eval(k(n.toString), m)))
    def str[T]: Cont[T, String => T, String] =
      Cont.shift[T, String => T, String](k => Cont.pure((s: String) => Cont.eval(k(s), m)))
    val format: Int => String => String = Cont.value(Cont.reset[Int => String => String, String, Int => String => String](
      int[String => String].flatMap(a => str[String].map(b => s"a=$a, b=$b"))), m)
    assertEquals(format(1)("x"), "a=1, b=x")
  }

class TestContSteps extends TestContOn(Steps)
class TestContRef extends TestContOn(Ref)
