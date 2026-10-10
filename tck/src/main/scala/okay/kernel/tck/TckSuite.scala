package okay.kernel.tck

import okay.kernel._
import okay.kernel.std.{Choose, Handlers, Reader, Results, State, Writer}
import Tck._

/**
 * THE TCK AS A SUITE: an implementation's `Effects` checked — the laws, the standard handlers written once, the
 * protocol, nesting through `others` (other implementations, each importing the next's computation), and the
 * random programs against the reference's digest
 */
abstract class TckSuite(name: String, e: Effects, others: List[Effects] = Nil) extends munit.FunSuite {
  private val st = State.of(Type.of("okay.kernel.tck.s"))
  private val log = Writer.of(Type.of("okay.kernel.tck.w"))
  private val rd = Reader.of(Type.of("okay.kernel.tck.r"))
  private val ask: Program[Integer] = Handlers.ask[Integer](rd)
  private def int(i: Int): Integer = Integer.valueOf(i)
  private def done[A](s: Step[A]): A = s match {
    case d: Step.Done[A @unchecked] => d.value()
    case other => throw new AssertionError(s"$name: expected a value, got $other")
  }
  private def yielded[A](s: Step[A]): Step.Yield[Integer, A] = s match {
    case y: Step.Yield[Integer @unchecked, A @unchecked] => y
    case other => throw new AssertionError(s"$name: expected a yield, got $other")
  }

  test(s"$name: the monad laws and shift0/$$'s") {
    val f = fn((x: Integer) => prog(_.pure(int(x + 1))))
    val g = fn((x: Integer) => prog(_.pure(int(x * 2))))
    def bind(p: Program[Integer], h: Fn[Integer, Program[Integer]]) = prog(x => x.flatMap(p.in(x), fn((a: Integer) => h(a).in(x))))
    val m = prog(_.pure(int(5)))
    assertEquals(run(e, bind(prog(_.pure(int(5))), f)), run(e, f(5)))
    assertEquals(run(e, bind(m, fn((a: Integer) => prog(_.pure(a))))), run(e, m))
    assertEquals(run(e, bind(bind(m, f), g)), run(e, bind(m, fn((a: Integer) => bind(f(a), g)))))
    val k = fn((x: Integer) => e.pure(int(x * 2)))
    assertEquals(run(e, prog(x => x.dollar[Integer, Integer, Integer](prog(_.pure(int(21))), fn((v: Integer) => x.pure(int(v * 2)))))).intValue, 42)
    val twice = prog(x => x.shift0[Integer, Integer, Integer](fn((kk: Fn[Integer, Prog[Integer]]) => x.flatMap(kk(int(1)), fn((a: Integer) => kk(a))))))
    assertEquals(run(e, prog(x => x.dollar[Integer, Integer, Integer](twice, fn((v: Integer) => x.pure(int(v + 10)))))).intValue, 21)
    assert(k != null)
  }

  def count(n: Int): Program[Integer] = prog { x =>
    if (n == 0) Handlers.get[Integer](st).in(x)
    else x.flatMap(Handlers.modify[Integer](st, fn((s: Integer) => int(s + 1))).in(x), fn((_: Void) => count(n - 1).in(x)))
  }

  test(s"$name: the standard handlers, written once — state 100 000 deep, writer, reader, choose") {
    assertEquals(run(e, Handlers.state[Integer, Integer](st, int(0), count(100000))).state().intValue, 100000)
    val body = prog { x =>
      x.flatMap(ask.in(x), fn((n: Integer) => x.flatMap(Handlers.tell[String](log, s"n=$n").in(x), fn((_: Void) => x.pure(n)))))
    }
    val r = run(e, Handlers.reader[Integer, Results.Logged[String, Integer]](rd, int(7), Handlers.writer[String, Integer](log, body)))
    assertEquals((r.log().get(0), r.value().intValue), ("n=7", 7))
    val pair = prog { x =>
      x.flatMap(Handlers.among[Integer](list(int(1), int(2))).in(x), fn((a: Integer) =>
        x.map(Handlers.among[Integer](list(int(10), int(20))).in(x), fn((b: Integer) => int(a * 100 + b)))))
    }
    assertEquals(run(e, Handlers.all(pair)), list(int(110), int(120), int(210), int(220)))
    assertEquals(run(e, Handlers.first(pair)).get().intValue, 110)
  }

  test(s"$name: failures are data; finalizers after a value, a failure, a raise") {
    var fins = 0
    val fin = prog { x => fins += 1; x.pure(null: Void) }
    val c = e.computation(prog(x => x.map(x.pure(int(1)), fn((_: Integer) => (throw new IllegalStateException("x")): Integer))), set())
    assert(c.start(Answers.NONE).isInstanceOf[Step.Failed[?]])
    assertEquals(run(e, prog(x => x.recover[Integer](prog(_.failed(new RuntimeException("no"))), fn((t: Throwable) => x.pure(int(t.getMessage.length)))))).intValue, 2)
    run(e, prog(x => x.ensuring(prog(_.pure(int(1))), fin)))
    run(e, prog(x => x.recover[Integer](prog(y => y.ensuring(prog(_.failed(new RuntimeException)), fin)), fn((_: Throwable) => x.pure(int(0))))))
    val err = okay.kernel.std.Throws.of(Type.of("okay.kernel.tck.t"))
    run(e, Handlers.throwsOf[String, Integer](err, prog(x => x.ensuring(Handlers.raise[String, Integer](err, "boom"), fin))))
    assertEquals(fins, 3)
  }

  test(s"$name: export — handed out, k multi-shot, the host answering in place, k.fail") {
    val c = e.computation(prog(x => x.map(ask.in(x), fn((n: Integer) => int(n + 1)))), set(rd))
    val y = yielded(c.start(Answers.NONE))
    assertEquals(done(y.k().resume(int(41))).intValue, 42)
    assertEquals(done(y.k().resume(int(1))).intValue, 2)
    val host = new Answers {
      def answers(t: Type): Boolean = t eq rd
      def value[X](op: Op[X]): X = Reader.narrow[Integer, X](op).fold(new Reader.Cases[Integer, X, X] { def ask(v: Same[Integer, X]): X = v(int(41)) })
    }
    assertEquals(done(c.start(host)).intValue, 42)
    val caught = e.computation(prog(x => x.recover[Integer](ask, fn((_: Throwable) => x.pure(int(-1))))), set(rd))
    assertEquals(done(yielded(caught.start(Answers.NONE)).k().fail(new RuntimeException)).intValue, -1)
  }

  test(s"$name: k.cancel — finalizers, not catchable; an uncancelable region waits") {
    var fins = 0
    val fin = prog { x => fins += 1; x.pure(null: Void) }
    val c = e.computation(prog(x => x.recover[Integer](prog(y => y.ensuring(ask, fin)), fn((_: Throwable) => x.pure(int(0))))), set(rd))
    assert(yielded(c.start(Answers.NONE)).k().cancel().isInstanceOf[Step.Cancelled[?]] && fins == 1)
    val u = e.computation(prog(x => x.uncancelable(ask)), set(rd))
    val wait = yielded(u.start(Answers.NONE)).k().cancel()
    assert(yielded(wait).k().resume(int(5)).isInstanceOf[Step.Cancelled[?]])
  }

  test(s"$name: a resume from another thread") {
    val c = e.computation(prog(x => x.map(ask.in(x), fn((n: Integer) => int(n * 2)))), set(rd))
    val y = yielded(c.start(Answers.NONE))
    var got: Step[Integer] = null
    val t = new Thread(new Runnable { def run(): Unit = got = y.k().resume(int(21)) })
    t.start(); t.join()
    assertEquals(done(got).intValue, 42)
  }

  test(s"$name: import, nesting through every implementation — in place and by k") {
    // the innermost asks and chooses; each implementation imports the next's computation; the outermost handles
    val inner: Effects = (e :: others).last
    val chain = (e :: others).reverse
    val innermost = inner.computation(prog { x =>
      x.flatMap(ask.in(x), fn((n: Integer) => x.map(Handlers.among[Integer](list(n, int(n + 1))).in(x), fn((v: Integer) => int(v * 10)))))
    }, set(rd, Choose.TYPE))
    val outer = chain.tail.foldLeft(innermost)((c, impl) => impl.computation(prog(x => x.foreign(c)), set(rd, Choose.TYPE)))
    assertEquals(run(e, Handlers.reader[Integer, java.util.List[Integer]](rd, int(4), Handlers.all(prog(x => x.foreign(outer))))), list(int(40), int(50)))
  }

  // -- where implementations went wrong: each a check of its own

  test(s"$name: k resumed under other handlers — an operation goes to the handler in force where it runs") {
    val tick = Type.of("okay.kernel.tck.tick")
    final case class Tick() extends Op[Void] { def effect(): Type = tick }
    val body = prog(x => x.flatMap(x.perform(Tick()), fn((_: Void) => ask.in(x))))
    // the clause resumes k under a reader of its own: the ask, in k, finds that one
    val h = new General[Integer, Integer] {
      def effect(): Type = tick
      def ret(a: Integer, x: Effects): Prog[Integer] = x.pure(a)
      def apply[X](op: Op[X], k: Fn[X, Prog[Integer]], x: Effects): Prog[Integer] =
        Handlers.reader[Integer, Integer](rd, int(2), prog(_ => k(null.asInstanceOf[X]))).in(x)
    }
    assertEquals(run(e, Handlers.reader[Integer, Integer](rd, int(1), prog(x => x.handle(h, body)))).intValue, 2)
  }

  test(s"$name: a shift0's body runs outside its delimiter — a handler between is not in force there") {
    val p = Handlers.reader[Integer, Integer](rd, int(1), prog { x =>
      x.dollar[Integer, Integer, Integer](
        Handlers.reader[Integer, Integer](rd, int(2), prog(y => y.shift0[Integer, Integer, Integer](fn((_: Fn[Integer, Prog[Integer]]) => ask.in(y))))),
        fn((v: Integer) => x.pure(v)))
    })
    assertEquals(run(e, p).intValue, 1)
  }

  test(s"$name: a failure of a handler's answer is the program's, caught by recover") {
    val none = Type.of("okay.kernel.tck.none")
    val h = new Returning[Integer, Integer] {
      def effect(): Type = none
      def answer(a: Integer): Integer = throw new IllegalStateException("answer")
    }
    assertEquals(run(e, prog(x => x.recover[Integer](prog(_.handle(h, prog(_.pure(int(1))))), fn((_: Throwable) => x.pure(int(7)))))).intValue, 7)
  }

  test(s"$name: a cancellation waiting in a region that fails: the region's end cancels still") {
    val c = e.computation(prog(x => x.recover[Integer](prog(y => y.uncancelable(prog(z => z.flatMap(ask.in(z),
      fn((_: Integer) => z.failed[Integer](new RuntimeException("region"))))))), fn((_: Throwable) => x.pure(int(0))))), set(rd))
    val wait = yielded(c.start(Answers.NONE)).k().cancel()
    assert(yielded(wait).k().resume(int(5)).isInstanceOf[Step.Cancelled[?]], "cancelled, not recovered")
  }

  test(s"$name: a foreign program ended by an abort crossing it — its finalizers run, their operations answered here") {
    val err = okay.kernel.std.Throws.of(Type.of("okay.kernel.tck.t2"))
    val inner = e.computation(prog(x => x.ensuring(Handlers.raise[String, Integer](err, "stop"), Handlers.tell[String](log, "fin"))), set(err, log))
    val p = Handlers.writer[String, Results.Result[String, Integer]](log, Handlers.throwsOf[String, Integer](err, prog(x => x.foreign(inner))))
    val r = run(e, p)
    assertEquals((r.log(), r.value().isInstanceOf[Results.Err[?, ?]]), (list("fin"), true))
  }

  test(s"$name: a thousand random programs, the reference's digest") {
    val os = outcomes(e)
    assert(os.count(_.isRight) > 700, s"${os.count(_.isRight)} of 1000 came to a value")
    assertEquals(digest(os), Tck.Digest)
  }
}
