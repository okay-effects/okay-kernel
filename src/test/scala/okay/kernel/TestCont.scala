package okay.kernel

import okay.Cont

/** SHIFT AND RESET WITH THE ANSWER TYPE MODIFIED, on every monad */
abstract class TestContOn(m: Monad) extends munit.FunSuite:

  test("reset(1 + shift(k => k(k(10)))) is 12: the answer kept") {
    val p = Cont.reset[Int, Int, Int](Cont.shift[Int, Int, Int](k => k(10).flatMap(k)).map(_ + 1))
    assertEquals(Cont.eval(p, m), 12)
  }

  test("the answer moved, Int to String: the body's answer an Int, the reset's a String") {
    val p = Cont.reset[String, Int, String](
      Cont.shift[Int, String, Int](k => Cont.pure("got " + Cont.eval(k(5), m))).map(_ + 1))
    assertEquals(Cont.eval(p, m), "got 6")
  }

  test("Danvy's printf: the answer moved once a hole, the format's type its arguments'") {
    def int[T]: Cont.Flat[T, Int => T, String] =
      Cont.shift[T, Int => T, String](k => Cont.pure((n: Int) => Cont.eval(k(n.toString), m)))
    def str[T]: Cont.Flat[T, String => T, String] =
      Cont.shift[T, String => T, String](k => Cont.pure((s: String) => Cont.eval(k(s), m)))
    val format: Int => String => String = Cont.eval(Cont.reset[Int => String => String, String, Int => String => String](
      int[String => String].flatMap(a => str[String].map(b => s"a=$a, b=$b"))), m)
    assertEquals(format(1)("x"), "a=1, b=x")
  }

class TestContSteps extends TestContOn(Steps)
class TestContRef extends TestContOn(Ref)

/** SHIFT0 AND $ WITH THE ANSWER TYPE MODIFIED, on every monad */
abstract class TestShift0On(m: Monad) extends munit.FunSuite:
  import okay.Cont as C

  test("$: the return clause, put back by each k — List(10, 20)") {
    val p = C.dollar[C[List[Int], List[Int], List[Int]], List[Int], List[Int], List[Int], Int](
      a => C.pure(List(a)),
      C.shift0[List[Int], List[Int], List[Int], C[List[Int], List[Int], List[Int]], Int](
        k => k(1).flatMap(xs => k(2).map(ys => xs ++ ys))).map(_ * 10))
    assertEquals(C.value(p, m), List(10, 20))
  }

  test("$ moving the answer: the body an Int, the return a String, the clause a String's length") {
    // ret: Int to C[String]; the clause takes k's String and answers its length — the answer Int at the end
    val p = C.dollar[C[String, String, String], Int, Int, Int, Int](
      a => C.pure[String, String]("n=" + a),
      C.shift0[String, String, String, C[Int, Int, Int], Int](k => C.pure(C.value(k(7), m).length)).map(_ * 6))
    assertEquals(C.value(p, m), "n=42".length)
  }

  test("two shift0 deep: the body outside both delimiters, dropping both — -1") {
    val inner = C.reset0[Int, C[Int, Int, Int], C[Int, Int, Int], Int, Int](
      C.shift0[Int, Int, Int, C[C[Int, Int, Int], C[Int, Int, Int], Int], Int](
        _ => C.shift0[Int, Int, Int, C[Int, Int, Int], Int](_ => C.pure(-1))).map(_ + 10))
    assertEquals(C.value(C.reset0[Int, Int, Int, Int, Int](inner.map(_ * 2)), m), -1)
  }

class TestShift0Steps extends TestShift0On(Steps)
class TestShift0Ref extends TestShift0On(Ref)

/** the same, written short: the answer's type given once, at a call the operation's value only */
abstract class TestContShortOn(m: Monad) extends munit.FunSuite:
  import okay.Cont

  test("shift0, short: reset0(1 + shift0(k => k(k(10)))) is 12") {
    val d = Cont.at[Int]
    assertEquals(Cont.value(d.reset0(d.shift0[Int](k => k(10).flatMap(k)).map(_ + 1)), m), 12)
  }
  test("$, short: List(10, 20)") {
    val d = Cont.at[List[Int]]
    assertEquals(Cont.value(d.dollar[Int](a => Cont.pure(List(a)), d.shift0[Int](k => k(1).flatMap(xs => k(2).map(xs ++ _))).map(_ * 10)), m),
      List(10, 20))
  }
  test("shift, short: the answer kept — 12; moved Int to String — got 6") {
    val d = Cont.at[Int]
    assertEquals(Cont.eval(d.reset(d.shift[Int](k => k(10).flatMap(k)).map(_ + 1)), m), 12)
    assertEquals(Cont.eval(d.reset(d.shiftTo[Int, String](k => Cont.pure("got " + Cont.eval(k(5), m))).map(_ + 1)), m), "got 6")
  }

  test("shift and shift0 in one program, one delimiter: reset0(shift0(k => k(1)) then shift(k => k(x + 10)), doubled) — 22") {
    val d = Cont.at[Int]
    val p = d.reset0(d.shift0[Int](k => k(1)).flatMap(x => d.shift[Int](k => k(x + 10))).map(_ * 2))
    assertEquals(Cont.value(p, m), 22)
  }

class TestContShortSteps extends TestContShortOn(Steps)
class TestContShortRef extends TestContShortOn(Ref)
