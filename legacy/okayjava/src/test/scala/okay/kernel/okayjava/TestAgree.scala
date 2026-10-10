package okay.kernel.okayjava

import okay.kernel.*
import okay.std.Handlers
import okay.kernel.std.{Effects}
import scala.util.Random
import scala.jdk.CollectionConverters.*

/**
 * EVERY MONAD AGREES: programs made at random — handlers of logs, states, failures and choices nested in any order,
 * the same effect again nearer, their operations, binds — each run on every monad, the values compared, or the
 * failures (an operation with no handler). The kernel's claim: the monad changes nothing a program sees
 */
class TestAgree extends munit.FunSuite:

  enum T:
    case Pure(n: Int)
    case Tell(e: Int, n: Int)
    case Get(e: Int)
    case Put(e: Int, n: Int)
    case Among(ns: List[Int])
    case Raise(e: Int, n: Int)
    case Seq(a: T, b: T)
    case Add(a: T, b: T)
    case Writer(e: Int, body: T)
    case State(e: Int, s0: Int, body: T)
    case Throws(e: Int, body: T)
    case Choose(body: T)

  // two effects of each kind, told apart by their types: an Int's and a String's
  private def writerE(e: Int) = Effects.writer(if e == 0 then "java.lang.Integer" else "java.lang.String")
  private def stateE(e: Int) = Effects.state(if e == 0 then "java.lang.Integer" else "java.lang.String")
  private def throwsE(e: Int) = Effects.throwsOf(if e == 0 then "java.lang.Integer" else "java.lang.String")

  def gen(r: Random, depth: Int): T =
    val leaf = depth <= 0 || r.nextInt(4) == 0
    if leaf then r.nextInt(6) match
      case 0 => T.Pure(r.nextInt(10))
      case 1 => T.Tell(r.nextInt(2), r.nextInt(10))
      case 2 => T.Get(r.nextInt(2))
      case 3 => T.Put(r.nextInt(2), r.nextInt(10))
      case 4 => T.Among(List.fill(1 + r.nextInt(2))(r.nextInt(10)))
      case _ => T.Raise(r.nextInt(2), r.nextInt(10))
    else r.nextInt(6) match
      case 0 => T.Seq(gen(r, depth - 1), gen(r, depth - 1))
      case 1 => T.Add(gen(r, depth - 1), gen(r, depth - 1))
      case 2 => T.Writer(r.nextInt(2), gen(r, depth - 1))
      case 3 => T.State(r.nextInt(2), r.nextInt(10), gen(r, depth - 1))
      case 4 => T.Throws(r.nextInt(2), gen(r, depth - 1))
      case _ => T.Choose(gen(r, depth - 1))

  /** the program in the monad: every value an Int, a handler's answer folded into one */
  def build(p: T, m: Monad): Prog[Int] = p match
    case T.Pure(n) => m.pure(n)
    case T.Tell(e, n) => m.map(m.perform(okay.kernel.std.Tell(n, writerE(e))), _ => n)
    case T.Get(e) => m.perform(okay.kernel.std.Get[Int](stateE(e)))
    case T.Put(e, n) => m.map(m.perform(okay.kernel.std.Put(n, stateE(e))), _ => n)
    case T.Among(ns) => m.perform(okay.kernel.std.Among(java.util.List.copyOf(ns.map(Int.box).asJava)).asInstanceOf[Op[Int]])
    case T.Raise(e, n) => m.map(m.perform(okay.kernel.std.Raise[Int, Int](n, throwsE(e))), x => x)
    case T.Seq(a, b) => m.flatMap(build(a, m), _ => build(b, m))
    case T.Add(a, b) => m.flatMap(build(a, m), x => m.map(build(b, m), y => x + y))
    case T.Writer(e, body) =>
      m.map(m.handle(Handlers.writer[Int, Int](writerE(e)), mm => build(body, mm)), (log, a) => a * 100 + log.sum)
    case T.State(e, s0, body) =>
      m.map(m.handle(Handlers.state[Int, Int](stateE(e), s0), mm => build(body, mm)), (s, a) => a * 10 + s)
    case T.Throws(e, body) => m.map(m.handle(Handlers.throws[Int, Int](throwsE(e)), mm => build(body, mm)), {
      case Right(a) => a
      case Left(err) => -1 - err
    })
    case T.Choose(body) => m.map(m.handle(Handlers.choose[Int], mm => build(body, mm)), _.sum)

  def outcome(p: T, m: Monad): Either[String, Int] =
    try Right(Program.run[Int](mm => build(p, mm), m))
    catch case e: IllegalStateException => Left("no handler")

  // the kernel's machine in Java among them (java/): its jar, binary, on the same API
  val monads = List("Steps" -> Steps, "Ref" -> Ref, "okay-java" -> OnJava.monad,
    "java" -> okay.jkernel.Monads.steps())

  test("a thousand programs at random: every monad agrees") {
    val r = Random(20261010)
    var differ = List.empty[String]
    var values = 0
    var digest = 0L
    for i <- 1 to 1000 do
      // a handler of each kind around, so most programs come to a value
      val p = T.Choose(T.Throws(0, T.Throws(1, T.State(0, 0, T.State(1, 0, T.Writer(0, T.Writer(1, gen(r, 6))))))))
      val outs = monads.map((n, m) => n -> outcome(p, m))
      if outs.head._2.isRight then values += 1
      digest = digest * 31 + outs.head._2.getOrElse(-1_000_000)
      if outs.map(_._2).distinct.size > 1 then differ = s"#$i $p: ${outs.mkString(", ")}" :: differ
    assert(differ.isEmpty, differ.take(3).mkString("\n"))
    // every handler in force, but a raise's own: most come to a value
    assert(values > 900, s"$values of 1000 came to a value")
    // the Java module's AgreeTest: the same programs (java.util.Random, the same seed), the same outcomes — two
    // implementations of the kernel, one behaviour
    assertEquals(digest, -8623788747993822161L)
  }
