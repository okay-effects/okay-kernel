package okay.kernel.tck

import scala.util.Random
import okay.kernel._
import okay.kernel.std.{Choose, Handlers, Reader, Results, State, Throws, Writer}

/**
 * THE TCK: what every implementation of the kernel is checked by, on its own `Effects`. A suite to extend —
 * `class TestMine extends TckSuite("mine", MyEffects, others = List(…))` — with the laws, the standard handlers
 * written once, the protocol, nesting through the other implementations given, and random programs whose outcomes'
 * digest is fixed: the reference machine's
 */
object Tck {
  def prog[A](f: Effects => Prog[A]): Program[A] = new Program[A] { def in(e: Effects): Prog[A] = f(e) }
  def fn[A, B](f: A => B): Fn[A, B] = new Fn[A, B] { def apply(a: A): B = f(a) }
  def set(ts: Type*): java.util.Set[Type] = { val s = new java.util.HashSet[Type](); ts.foreach(s.add); s }
  def list[A](as: A*): java.util.List[A] = { val l = new java.util.ArrayList[A](); as.foreach(l.add); l }

  /** a program run to its value on `e`; a failure thrown; an operation with no handler, a failure */
  def run[A](e: Effects, p: Program[A]): A = e.computation(p, set()).start(Answers.NONE) match {
    case d: Step.Done[A @unchecked] => d.value()
    case f: Step.Failed[A @unchecked] => throw f.error()
    case _: Step.Cancelled[A @unchecked] => throw new IllegalStateException("cancelled")
    case y: Step.Yield[?, ?] => throw new NoHandler(y.op())
  }
  final class NoHandler(op: Op[?]) extends RuntimeException("an operation with no handler: " + op)

  // -- random programs: handlers of logs, states, failures and choices nested in any order, the same effect twice
  // (two identities each), their operations, binds

  sealed trait T
  final case class Pure(n: Int) extends T
  final case class Tell(e: Int, n: Int) extends T
  final case class Get(e: Int) extends T
  final case class Put(e: Int, n: Int) extends T
  final case class Among(ns: List[Int]) extends T
  final case class Raise(e: Int, n: Int) extends T
  final case class Seq(a: T, b: T) extends T
  final case class Add(a: T, b: T) extends T
  final case class WriterH(e: Int, body: T) extends T
  final case class StateH(e: Int, s0: Int, body: T) extends T
  final case class ThrowsH(e: Int, body: T) extends T
  final case class ChooseH(body: T) extends T

  private def tag(kind: String, e: Int): Type = Type.of("okay.kernel.tck." + kind + e)
  def writerE(e: Int): Type = Writer.of(tag("w", e))
  def stateE(e: Int): Type = State.of(tag("s", e))
  def throwsE(e: Int): Type = Throws.of(tag("t", e))

  def gen(r: Random, depth: Int): T =
    if (depth <= 0 || r.nextInt(4) == 0) r.nextInt(6) match {
      case 0 => Pure(r.nextInt(10))
      case 1 => Tell(r.nextInt(2), r.nextInt(10))
      case 2 => Get(r.nextInt(2))
      case 3 => Put(r.nextInt(2), r.nextInt(10))
      case 4 => Among(List.fill(1 + r.nextInt(2))(r.nextInt(10)))
      case _ => Raise(r.nextInt(2), r.nextInt(10))
    }
    else r.nextInt(6) match {
      case 0 => Seq(gen(r, depth - 1), gen(r, depth - 1))
      case 1 => Add(gen(r, depth - 1), gen(r, depth - 1))
      case 2 => WriterH(r.nextInt(2), gen(r, depth - 1))
      case 3 => StateH(r.nextInt(2), r.nextInt(10), gen(r, depth - 1))
      case 4 => ThrowsH(r.nextInt(2), gen(r, depth - 1))
      case _ => ChooseH(gen(r, depth - 1))
    }

  private def int(i: Int): Integer = Integer.valueOf(i)

  /** the program, written once over `Effects`: every value an Int, a handler's answer folded into one */
  def build(t: T): Program[Integer] = t match {
    case Pure(n) => prog(_.pure(int(n)))
    case Tell(e, n) => prog(x => x.map(Handlers.tell[Integer](writerE(e), int(n)).in(x), fn((_: Void) => int(n))))
    case Get(e) => Handlers.get[Integer](stateE(e))
    case Put(e, n) => prog(x => x.map(Handlers.put[Integer](stateE(e), int(n)).in(x), fn((_: Void) => int(n))))
    case Among(ns) => Handlers.among[Integer](list(ns.map(int): _*))
    case Raise(e, n) => Handlers.raise[Integer, Integer](throwsE(e), int(n))
    case Seq(a, b) => prog(x => x.flatMap(build(a).in(x), fn((_: Integer) => build(b).in(x))))
    case Add(a, b) => prog(x => x.flatMap(build(a).in(x), fn((i: Integer) => x.map(build(b).in(x), fn((j: Integer) => int(i + j))))))
    case WriterH(e, body) => prog(x => x.map(Handlers.writer[Integer, Integer](writerE(e), build(body)).in(x),
      fn((l: Results.Logged[Integer, Integer]) => { var s = 0; l.log().forEach(w => s += w); int(l.value() * 100 + s) })))
    case StateH(e, s0, body) => prog(x => x.map(Handlers.state[Integer, Integer](stateE(e), int(s0), build(body)).in(x),
      fn((f: Results.Final[Integer, Integer]) => int(f.value() * 10 + f.state()))))
    case ThrowsH(e, body) => prog(x => x.map(Handlers.throwsOf[Integer, Integer](throwsE(e), build(body)).in(x),
      fn((r: Results.Result[Integer, Integer]) => r.fold[Integer](new Results.Fn2[Integer, Integer] { def apply(a: Integer): Integer = a },
        new Results.Fn2[Integer, Integer] { def apply(err: Integer): Integer = int(-1 - err) }))))
    case ChooseH(body) => prog(x => x.map(Handlers.all[Integer](build(body)).in(x), fn((l: java.util.List[Integer]) => { var s = 0; l.forEach(v => s += v); int(s) })))
  }

  /** a thousand programs, a handler of each kind around most: their outcomes on `e` — a value, or no handler */
  def outcomes(e: Effects, n: Int = 1000, seed: Long = 20261010L): Vector[Either[String, Int]] = {
    val r = new Random(seed)
    Vector.fill(n) {
      val t = ChooseH(ThrowsH(0, ThrowsH(1, StateH(0, 0, StateH(1, 0, WriterH(0, WriterH(1, gen(r, 6))))))))
      try Right(run(e, build(t)).intValue) catch { case _: NoHandler => Left("no handler") }
    }
  }

  def digest(os: Vector[Either[String, Int]]): Long = os.foldLeft(0L)((d, o) => d * 31 + o.getOrElse(-1000000))

  /** the reference machine's digest of the thousand programs: every implementation's must be it — the same as the
   * legacy kernel's and okay-java's AgreeTest (the same programs, the same seed): the new kernel answers as they did */
  val Digest: Long = -8623788747993822161L
}
