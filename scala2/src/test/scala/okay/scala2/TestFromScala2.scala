package okay.scala2

import okay.kernel.{Monad, Program, Steps}
import okay.std.{Effects, Handlers, Tell}
import okay.demo.Programs

/** THE KERNEL FROM SCALA 2: Scala 3's program run, and one of Scala 2 under Scala 3's handlers */
class TestFromScala2 extends munit.FunSuite {
  def under(m: Monad, p: Program[_]): Any =
    Program.run(new Program[Any] {
      def in(mm: Monad): Any = mm.handle(Handlers.writer(Effects.writer(classOf[String])), new Program[Any] { def in(m2: Monad): Any = m2.handle(Handlers.async, p) })
    }, m)

  test("Scala 3's program, Int ! Writer % String + Async, run from Scala 2") {
    assertEquals(under(Steps, Programs.p(5)), (List("got 5", "awaited 6"), 12))
  }

  test("a program written in Scala 2, its operations Scala 3's data") {
    val q: Program[Any] = new Program[Any] {
      def in(m: Monad): Any = m.flatMap(m.perform(Tell("scala2", Effects.writer(classOf[String]))), (_: Any) => m.pure(7))
    }
    assertEquals(under(Steps, q), (List("scala2"), 7))
  }
}
