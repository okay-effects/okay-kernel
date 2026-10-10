package okay.kernel

import java.util.concurrent.{CompletableFuture, TimeUnit}
import okay.{!, %, +, Eff}
import okay.std.*
import okay.kernel.std.{Await, Effects, Logged, Tell}
import okay.jkernel.{Monads as JavaMonads}
import okay.jkernel.demo.{Programs as JavaPrograms}
import okay.jkernel.std.{Async as JavaAsync, Handlers as JavaHandlers}
import okay.demo.Programs

/**
 * BINARY COMPATIBILITY: the Java module's jar (`java/`) — compiled by javac against the same `okay-kernel-api` —
 * with this one's, in one run. Programs of each on the machines of the other, under the handlers of the other, both
 * nested in one: one class an interface, an operation, an answer, so nothing between them is translated
 */
class TestBinary extends munit.FunSuite:
  val javas: Monad = JavaMonads.steps()

  test("Scala 3's program, its Scala handlers, on Java's machine") {
    assertEquals(Eff.run(Programs.handled(1), javas), (List("got 1", "awaited 2"), 4))
  }

  test("Java's program under Scala 3's typed handlers, on either machine") {
    val q: Int ! Writer % String + Async = Eff.from(JavaPrograms.p(1).asInstanceOf[Program[Int]])
    for m <- List(Steps, javas) do
      assertEquals(Eff.run(writer(async(q)), m), (List("java got 1", "java awaited 2"), 4))
  }

  test("Scala 3's program under Java's handlers, on either machine: the API's Logged") {
    for m <- List(Steps, javas) do
      val r = Program.run(JavaPrograms.logged(Programs.p(1)), m)
      assertEquals(r.log, java.util.List.of("got 1", "awaited 2"))
      assertEquals(r.value: Any, 4: Any)
  }

  test("one program, operations of both, handlers of both nested: Java's writer outside, Scala's inside") {
    val both: Int ! Writer % String + Async = Programs.p(1).flatMap(a => Eff.from(JavaPrograms.p(a).asInstanceOf[Program[Int]]))
    val inner: Int ! Writer % String = async(both)
    for m <- List(Steps, javas) do
      // Scala's writer the nearer: it takes every tell, Java's and Scala's
      val r: Logged[String, (List[String], Int)] = Program.run(m2 => m2.handle(
        JavaHandlers.writer[String, (List[String], Int)](Effects.writer(classOf[String])), writer(inner)), m)
      assertEquals(r.value, (List("got 1", "awaited 2", "java got 4", "java awaited 5"), 10))
      assertEquals(r.log, java.util.List.of[String]())
  }

  test("Java's prompts on Scala's machine, Scala's on Java's") {
    assertEquals(Program.run(JavaPrograms.shifted(), Steps): Any, 41: Any)
    assertEquals(Eff.run(reset[Int, Any](p => p.shift[Int](k => k(10).flatMap(k)).map(_ * 2)).map(_ + 1), javas), 41)
  }

  test("Java's async at the top, a Scala program awaiting a callback from another thread, Scala's machine") {
    val later: Int ! Writer % String + Async =
      tell("start").andThen(await[Int](cb => CompletableFuture.runAsync(() => cb.ok(41)))).flatMap(x => tell(s"got $x").map(_ => x + 1))
    val done = CompletableFuture[(List[String], Int)]()
    JavaAsync.start(writer(later), Steps, done.complete(_), done.completeExceptionally(_))
    assertEquals(done.get(5, TimeUnit.SECONDS), (List("start", "got 41"), 42))
  }

  test("Cont: Java's shift0 under Scala's reset0, Scala's shift0 under Java's — one delimiter, the effect Reset0") {
    val javaShift = okay.Cont.from[okay.Cont[Int, Int, Int], okay.Cont[Int, Int, Int], Int](
      JavaPrograms.shift0Twice().program().asInstanceOf[Program[Int]])
    for m <- List(Steps, javas) do
      assertEquals(okay.Cont.value(okay.Cont.reset0[Int, Int, Int, Int, Int](javaShift.map(_ + 1)), m), 12)
    val scalaShift = okay.Cont.shift0[Int, Int, Int, okay.Cont[Int, Int, Int], Int](k => k(10).flatMap(k))
    assertEquals(JavaPrograms.plusOneReset0(scalaShift.program().asInstanceOf[Program[Integer]]): Any, 12: Any)
  }

  test("levels: Java's lexical program, its capabilities and prompts, in Scala's lexical layer") {
    val p = okay.lexical.Prog.of[okay.lexical.Top, Integer](JavaPrograms.shifted())
    for m <- List(Steps, javas) do assertEquals(okay.lexical.Prog.run(p, m): Any, 41: Any)
  }
