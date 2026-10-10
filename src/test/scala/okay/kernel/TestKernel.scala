package okay.kernel

import okay.{!, %, +, Eff}
import okay.std.*

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

  test("multi-shot: a clause resuming twice, each run the handler's again") {
    object Flip extends Op[Boolean]:
      def effect: Effect = flip
    lazy val flip: Effect = Effect("Flip")
    val all: Handler = new Handler:
      def effect: Effect = flip
      def ret(a: Any, mm: Monad): Any = mm.pure(List(a))
      def apply(op: Op[?], k: Fn[Any, Any], mm: Monad): Any =
        mm.flatMap(k(true), (a: Any) => mm.map(k(false), (b: Any) => a.asInstanceOf[List[Any]] ++ b.asInstanceOf[List[Any]]))
    val coins: Program[Any] = mm => mm.flatMap(mm.perform(Flip), x => mm.map(mm.perform(Flip), y => (x, y)))
    assertEquals(Program.run(new Program[Any] { def in(mm: Monad) = mm.handle(all, coins) }, m),
      List((true, true), (true, false), (false, true), (false, false)))
  }

  test("an operation with no handler: an error") {
    intercept[IllegalStateException](Program.run(new Program[Unit] { def in(mm: Monad) = mm.perform(Tell("x")) }, m))
  }

class TestKernelSteps extends TestKernel(Steps, 100_000)
/** the reference, recursive: programs as deep as a platform's stack lets it */
class TestKernelRef extends TestKernel(Ref, 300)
