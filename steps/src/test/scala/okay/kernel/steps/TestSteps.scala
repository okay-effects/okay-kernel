package okay.kernel.steps

import munit.FunSuite
import okay.kernel._
import okay.kernel.std.{Handlers, Results, State}
import okay.kernel.types.typeOf

/** the reference machine: the standard handlers written once, control, failures, finalizers, cancellation, the
 * protocol — export, import, multi-shot k */
class TestSteps extends FunSuite {
  val e: Effects = Steps
  val st: Key[State[Integer]] = State.key[Integer](typeOf[Integer])
  val log: Key[okay.kernel.std.Writer[String]] = okay.kernel.std.Writer.key[String](typeOf[String])
  val err: Key[okay.kernel.std.Throws[String]] = okay.kernel.std.Throws.key[String](typeOf[String])
  val rd: Key[okay.kernel.std.Reader[Integer]] = okay.kernel.std.Reader.key[Integer](typeOf[Integer])

  def prog[A](f: Effects => Prog[A]): Program[A] = new Program[A] { def in(e: Effects): Prog[A] = f(e) }
  def fn[A, B](f: A => B): Fn[A, B] = new Fn[A, B] { def apply(a: A): B = f(a) }
  def run[A](p: Program[A]): A = Steps.run(p)
  def set(ks: Key[?]*): java.util.Set[Type] = { val s = new java.util.HashSet[Type](); ks.foreach(k => s.add(k.`type`())); s }
  /** a row as Scala's set: java.util's equals of sets (containsAll) fails on Scala Native under Scala 3 */
  def elems(s: java.util.Set[Type]): Set[Type] = { val b = Set.newBuilder[Type]; val i = s.iterator(); while (i.hasNext) b += i.next(); b.result() }

  def count(n: Int): Program[Integer] = prog { e =>
    if (n == 0) Handlers.get[Integer](st).in(e)
    else e.flatMap(Handlers.modify[Integer](st, fn((s: Integer) => Integer.valueOf(s + 1))).in(e), fn((_: Void) => count(n - 1).in(e)))
  }

  test("state as a cell, 100 000 operations in constant stack") {
    val r = run(Handlers.state[Integer, Integer](st, 0, count(100000)))
    assertEquals(r.state().intValue, 100000)
  }

  test("writer and state, handled in either order; reader answered in place") {
    val body = prog { e =>
      e.flatMap(Handlers.ask[Integer](rd).in(e), fn((n: Integer) =>
        e.flatMap(Handlers.tell[String](log, s"n=$n").in(e), fn((_: Void) =>
          e.flatMap(Handlers.put[Integer](st, n).in(e), fn((_: Void) => Handlers.get[Integer](st).in(e)))))))
    }
    val r = run(Handlers.reader[Integer, Results.Logged[String, Results.Final[Integer, Integer]]](rd, 7,
      Handlers.writer[String, Results.Final[Integer, Integer]](log, Handlers.state[Integer, Integer](st, 0, body))))
    assertEquals(r.log(), java.util.Arrays.asList("n=7"))
    assertEquals(r.value().state().intValue, 7)
  }

  test("a raise ends the body, its finalizer run; handleWith answers it") {
    var finalized = false
    val body = prog { e => e.ensuring(Handlers.raise[String, Integer](err, "boom"), prog { e2 => finalized = true; e2.pure(null: Void) }) }
    val r = run(Handlers.throwsOf[String, Integer](err, body))
    assert(r.isInstanceOf[Results.Err[?, ?]] && finalized)
    assertEquals(run(Handlers.handleWith[String, Integer](err, body, fn((m: String) => prog(_.pure(Integer.valueOf(m.length)))))).intValue, 4)
  }

  test("choose: every path; the first") {
    val pair = prog { e =>
      e.flatMap(Handlers.among[Integer](java.util.Arrays.asList(1, 2)).in(e), fn((a: Integer) =>
        e.map(Handlers.among[Integer](java.util.Arrays.asList(10, 20)).in(e), fn((b: Integer) => Integer.valueOf(a * 100 + b)))))
    }
    assertEquals(run(Handlers.all(pair)), java.util.Arrays.asList[Integer](110, 120, 210, 220))
    assertEquals(run(Handlers.first(pair)).get().intValue, 110)
  }

  test("shift0 and $: the laws") {
    val k = fn((x: Integer) => Steps.pure(Integer.valueOf(x * 2)))
    assertEquals(run(prog(e => e.dollar[Integer, Integer, Integer](prog(_.pure(Integer.valueOf(21))), k))).intValue, 42)
    val shifted = prog(e => e.shift0[Integer, Integer, Integer](fn((kk: Fn[Integer, Prog[Integer]]) => e.flatMap(kk(1), fn((a: Integer) => kk(a))))))
    assertEquals(run(prog(e => e.dollar[Integer, Integer, Integer](shifted, fn((x: Integer) => e.pure(Integer.valueOf(x + 10)))))).intValue, 21)
  }

  test("a generator: values pulled one by one") {
    val em = okay.kernel.std.Emit.key[Integer](typeOf[Integer])
    val three = prog { e =>
      e.flatMap(Handlers.send[Integer](em, 1).in(e), fn((_: Void) => e.flatMap(Handlers.send[Integer](em, 2).in(e), fn((_: Void) => e.pure("done")))))
    }
    def pull(g: Results.Gen[Integer, String], acc: List[Int]): (List[Int], String) = g match {
      case n: Results.Next[Integer @unchecked, String @unchecked] => pull(run(n.rest()), acc :+ n.value().intValue)
      case end: Results.End[Integer @unchecked, String @unchecked] => (acc, end.value())
    }
    assertEquals(pull(run(Handlers.generate[Integer, String](em, three)), Nil), (List(1, 2), "done"))
  }

  test("a failure is data; recover catches it; an exception of a function is one") {
    val c = Steps.computation(prog(e => e.map(e.pure(Integer.valueOf(1)), fn((_: Integer) => (throw new IllegalStateException("x")): Integer))), set())
    assert(c.start(Answers.NONE).isInstanceOf[Step.Failed[?]])
    assertEquals(run(prog(e => e.recover[Integer](prog(_.failed(new RuntimeException("no"))), fn((t: Throwable) => e.pure(Integer.valueOf(t.getMessage.length)))))).intValue, 2)
  }

  /** an operation nothing here handles: handed out */
  val ask: Program[Integer] = Handlers.ask[Integer](rd)

  test("export: an operation handed out, k resumed — twice, multi-shot") {
    val c = Steps.computation(prog(e => e.map(ask.in(e), fn((n: Integer) => Integer.valueOf(n + 1)))), set(rd))
    c.start(Answers.NONE) match {
      case y: Step.Yield[?, ?] =>
        val k = y.k().asInstanceOf[Resume[Integer, Integer]]
        assertEquals(k.resume(41).asInstanceOf[Step.Done[Integer]].value().intValue, 42)
        assertEquals(k.resume(1).asInstanceOf[Step.Done[Integer]].value().intValue, 2)
        assertEquals(elems(c.effects()), Set(rd.`type`()))
      case s => fail(s"expected a yield: $s")
    }
  }

  test("export: answered in place by the host, no yield") {
    val host = new Answers {
      def answers(t: Type): Boolean = t eq rd.`type`()
      def value[X](op: Op[X]): X = Integer.valueOf(41).asInstanceOf[X]
    }
    val c = Steps.computation(prog(e => e.map(ask.in(e), fn((n: Integer) => Integer.valueOf(n + 1)))), set(rd))
    assertEquals(c.start(host).asInstanceOf[Step.Done[Integer]].value().intValue, 42)
  }

  test("k.fail: the computation catches it where the operation was") {
    val c = Steps.computation(prog(e => e.recover[Integer](ask, fn((t: Throwable) => e.pure(Integer.valueOf(-1))))), set(rd))
    val y = c.start(Answers.NONE).asInstanceOf[Step.Yield[Integer, Integer]]
    assertEquals(y.k().fail(new RuntimeException("x")).asInstanceOf[Step.Done[Integer]].value().intValue, -1)
  }

  test("k.cancel: finalizers run, not catchable; inside uncancelable it waits") {
    var finalized = 0
    val fin = prog { e => finalized += 1; e.pure(null: Void) }
    val c = Steps.computation(prog(e => e.recover[Integer](prog(e2 => e2.ensuring(ask, fin)), fn((_: Throwable) => e.pure(Integer.valueOf(0))))), set(rd))
    val y = c.start(Answers.NONE).asInstanceOf[Step.Yield[Integer, Integer]]
    assert(y.k().cancel().isInstanceOf[Step.Cancelled[?]] && finalized == 1, "cancelled, finalizer once, recover not taken")
    val u = Steps.computation(prog(e => e.uncancelable(ask)), set(rd))
    val wait = u.start(Answers.NONE).asInstanceOf[Step.Yield[Integer, Integer]].k().cancel()
    assert(wait.isInstanceOf[Step.Yield[?, ?]], "the region waits")
    assert(wait.asInstanceOf[Step.Yield[Integer, Integer]].k().resume(5).isInstanceOf[Step.Cancelled[?]], "its end cancels")
  }

  test("import: another's program under this one's handlers — in place and by k") {
    val inner = Steps.computation(prog { e =>
      e.flatMap(ask.in(e), fn((n: Integer) => e.map(Handlers.among[Integer](java.util.Arrays.asList(n, n + 1)).in(e), fn((x: Integer) => Integer.valueOf(x * 10)))))
    }, set(rd, okay.kernel.std.Choose.KEY))
    val outer = Handlers.reader[Integer, java.util.List[Integer]](rd, 4, Handlers.all(prog(e => e.foreign(inner))))
    assertEquals(run(outer), java.util.Arrays.asList[Integer](40, 50))
  }
}
