package okay.scala2

import okay.kernel.{Fn, Monad, Op, Prog, Program, Steps}
import okay.std.{Effects, Handlers, Tell}
import okay.demo.Programs

/** THE KERNEL FROM SCALA 2, TYPED: Scala 3's program run, and one of Scala 2 under Scala 3's handlers */
class TestFromScala2 extends munit.FunSuite {
  def under[A](m: Monad, p: Program[A]): (List[String], A) =
    Program.run(new Program[(List[String], A)] {
      def in(mm: Monad): Prog[(List[String], A)] =
        mm.handle(Handlers.writer[String, A](Effects.writer(classOf[String])), new Program[A] {
          def in(m2: Monad): Prog[A] = m2.handle(Handlers.async[A], p)
        })
    }, m)

  test("Scala 3's program, Int ! Writer % String + Async, run from Scala 2") {
    assertEquals(under(Steps, Programs.p(5)), (List("got 5", "awaited 6"), 12))
  }

  test("a program written in Scala 2, its operations Scala 3's data") {
    val q: Program[Int] = new Program[Int] {
      def in(m: Monad): Prog[Int] = m.flatMap(m.perform(Tell("scala2", Effects.writer(classOf[String]))), (_: Unit) => m.pure(7))
    }
    assertEquals(under(Steps, q), (List("scala2"), 7))
  }
}
