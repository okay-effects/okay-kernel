package okay.kernel

import okay.{!, %, +, Eff}
import okay.std.*
import okay.kernel.std.{Effects, Tell}

import okay.demo.Programs

/** THE KERNEL'S BEHAVIOUR, one suite, every monad: the same programs, the same values */
abstract class TestKernel(m: Monad, big: Int) extends munit.FunSuite:
  def run[A](p: A ! Any): A = Eff.run(p, m)

  test("the goal's program: Int ! Writer % String + Async, its handlers around it") {
    assertEquals(run(Programs.handled(1)), (List("got 1", "awaited 2"), 4))
  }

  test("a raise drops the rest, the log kept outside it") {
    val q: Int ! Writer % String + Throws % String =
      tell("before").andThen(raise("no")).andThen(tell("after")).map(_ => 1)
    assertEquals(run(writer(attempt(q))), (List("before"), Left("no")))
    // the other way round: the raise's handler outside, the log dropped with the rest
    assertEquals(run(attempt(writer(q))), Left("no"))
  }

  test("binds in constant stack, and the handler of each operation the nearest") {
    def loop(i: Int): Unit ! Writer % Int = if i == 0 then Eff.pure(()) else tell(i).andThen(Eff.delay(loop(i - 1)))
    val (log, _) = run(writer(loop(big)))
    assertEquals(log.length, big)
    assertEquals(log.head, big)
  }

  test("a handler of the same effect nearer takes it: the outer one never") {
    val q: Unit ! Writer % String = writer[String, Writer[String], Unit](tell("inner")).flatMap((log, _) => tell(s"outer saw $log"))
    assertEquals(run(writer(q)), (List("outer saw List(inner)"), ()))
  }

  test("multi-shot: a clause resuming twice, each run the handler's again — on the kernel's typed API") {
    type P = (Boolean, Boolean)
    final case class Flip() extends Op[Boolean]:
      def effect: Effect = TestKernel.flip
    val all: Handler[P, List[P]] = new Handler[P, List[P]]:
      def effect: Effect = TestKernel.flip
      def ret(a: P, mm: Monad): Prog[List[P]] = mm.pure(List(a))
      def apply[X](op: Op[X], k: Fn[X, Prog[List[P]]], mm: Monad): Prog[List[P]] = op match
        case Flip() => mm.flatMap(k(true), a => mm.map(k(false), b => a ++ b))
    val coins: Program[P] = mm => mm.flatMap(mm.perform(Flip()), x => mm.map(mm.perform(Flip()), y => (x, y)))
    assertEquals(Program.run[List[P]](mm => mm.handle(all, coins), m), List((true, true), (true, false), (false, true), (false, false)))
  }

  test("an operation with no handler: an error") {
    intercept[IllegalStateException](Program.run(new Program[Void] {
      def in(mm: Monad) = mm.perform(Tell("x", Effects.writer(classOf[String])))
    }, m))
  }

  // EFFECTS BY TYPE

  test("Writer % String and Writer % Int: two effects, each to its own handler") {
    val q: Unit ! Writer % String + Writer % Int = tell("a").andThen(tell(1)).andThen(tell("b")).andThen(tell(2))
    // the Int's handler inner, the String's outer: each takes its own, past the other
    assertEquals(run(writer[String, Any, (List[Int], Unit)](writer[Int, Writer[String], Unit](q))), (List("a", "b"), (List(1, 2), ())))
  }

  test("a state of a cell; a choice, every path") {
    val counted: Int ! State % Int = get[Int].flatMap(s => put(s + 10)).andThen(get[Int])
    assertEquals(run(state(5, counted)), (15, 15))
    val pairs: (Int, String) ! Choose = among(Seq(1, 2)).flatMap(x => among(Seq("a", "b")).map(y => (x, y)))
    assertEquals(run(choose(pairs)), List((1, "a"), (1, "b"), (2, "a"), (2, "b")))
  }

  // DELIMITED CONTROL, BY PROMPTS: FRESH EFFECTS

  test("shift: 1 + reset(2 * shift(k => k(k(10)))) is 41") {
    assertEquals(run(reset[Int, Any](p => p.shift[Int](k => k(10).flatMap(k)).map(_ * 2)).map(_ + 1)), 41)
  }

  test("shift to a reset further out, crossing a nearer one, by its prompt") {
    assertEquals(run(reset[Int, Any](outer => reset[Int, Ctl](_ => outer.shift[Int](k => k(1).map(_ + 1000)).map(_ * 10)).map(_ / 10))), 1001)
  }

  test("shift's clause under the reset again, shift0's outside it") {
    // reset(reset(shift k1. shift k2. k2(k2(k1 1))) + 10) * 2): k2 the empty rest under the reset — 22
    assertEquals(run(reset[Int, Any](_ => reset[Int, Ctl](p => p.shift[Int](k1 => p.shift[Int](k2 => k1(1).flatMap(k2).flatMap(k2))).map(_ + 10)).map(_ * 2))), 22)
  }

  test("multi-shot by shift: k resumed twice, the answers joined") {
    assertEquals(run(reset[List[Int], Any](p => p.shift[Int](k => k(1).flatMap(a => k(2).map(b => a ++ b))).map(x => List(x, x * 10)))),
      List(1, 10, 2, 20))
  }

  // CONT0: THE ANSWER TYPE MODIFIED, STATICALLY

  test("cont0: reset0(1 + shift0(k => k(k(10)))) is 12; the answer type Int to String") {
    val p = okay.Cont.reset0[Int, Int, Int, Int, Int](
      okay.Cont.shift0[Int, Int, Int, okay.Cont[Int, Int, Int], Int](k => k(10).flatMap(k)).map(_ + 1))
    assertEquals(okay.Cont.value(p, m), 12)
    val q = okay.Cont.reset0[String, String, String, String, String](
      okay.Cont.shift0[String, String, String, okay.Cont[String, String, String], Int](k => k(5)).map(_ * 2).map(_.toString))
    assertEquals(okay.Cont.value(q, m), "10")
  }

  test("cont0: printf, the answer modified once a hole") {
    import okay.Cont as C
    def d[S, T]: C[C[T, T, T], C[S, S, Int => T], String] =
      C.shift0[T, T, T, C[S, S, Int => T], String](k => C.pure[S, Int => T](x => C.value(k(x.toString), m)))
    def s[S, T]: C[C[T, T, T], C[S, S, String => T], String] =
      C.shift0[T, T, T, C[S, S, String => T], String](k => C.pure[S, String => T](x => C.value(k(x), m)))
    val format: Int => String => String = C.value(C.reset0(
      d[Int => String => String, String => String].flatMap(a => s[String => String, String].map(b => s"a=$a, b=$b"))), m)
    assertEquals(format(1)("x"), "a=1, b=x")
  }

  // ASYNC

  test("async at the top: callbacks answered at once, in constant stack") {
    def loop(i: Int, acc: Int): Int ! Async = if i == 0 then Eff.pure(acc) else await[Int](cb => cb.ok(1)).flatMap(x => Eff.delay(loop(i - 1, acc + x)))
    var r = -1
    Async.start(loop(big, 0), m, a => r = a, e => throw e)
    assertEquals(r, big)
  }

  test("async at the top: a failure at a callback the run's") {
    var e: Throwable = null
    Async.start(await[Int](cb => cb.fail(IllegalStateException("cb"))), m, _ => (), x => e = x)
    assertEquals(e.getMessage, "cb")
  }

object TestKernel:
  val flip: Effect = Effect("Flip")

class TestKernelSteps extends TestKernel(Steps, 100_000)
/** the reference, recursive: programs as deep as a platform's stack lets it */
class TestKernelRef extends TestKernel(Ref, 300)
