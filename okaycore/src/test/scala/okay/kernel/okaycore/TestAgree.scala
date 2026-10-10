package okay.kernel.okaycore

import okay.kernel.*
import okay.std.{Among, Effects, Get, Handlers, Put, Raise, Tell}
import scala.util.Random

/**
 * EVERY MONAD AGREES: programs made at random — handlers of logs, states, failures and choices nested in any order,
 * the same effect again nearer, their operations, binds — each run on every monad, the values compared, or the
 * failures (an operation with no handler). The kernel's claim: the monad changes nothing a program sees
 */
class TestAgree extends munit.FunSuite:

  enum Prog:
    case Pure(n: Int)
    case Tell(e: Int, n: Int)
    case Get(e: Int)
    case Put(e: Int, n: Int)
    case Among(ns: List[Int])
    case Raise(e: Int, n: Int)
    case Seq(a: Prog, b: Prog)
    case Add(a: Prog, b: Prog)
    case Writer(e: Int, body: Prog)
    case State(e: Int, s0: Int, body: Prog)
    case Throws(e: Int, body: Prog)
    case Choose(body: Prog)

  // two effects of each kind, told apart by their types: an Int's and a String's
  private def writerE(e: Int) = Effects.writer(if e == 0 then "java.lang.Integer" else "java.lang.String")
  private def stateE(e: Int) = Effects.state(if e == 0 then "java.lang.Integer" else "java.lang.String")
  private def throwsE(e: Int) = Effects.throws(if e == 0 then "java.lang.Integer" else "java.lang.String")

  def gen(r: Random, depth: Int): Prog =
    val leaf = depth <= 0 || r.nextInt(4) == 0
    if leaf then r.nextInt(6) match
      case 0 => Prog.Pure(r.nextInt(10))
      case 1 => Prog.Tell(r.nextInt(2), r.nextInt(10))
      case 2 => Prog.Get(r.nextInt(2))
      case 3 => Prog.Put(r.nextInt(2), r.nextInt(10))
      case 4 => Prog.Among(List.fill(1 + r.nextInt(2))(r.nextInt(10)))
      case _ => Prog.Raise(r.nextInt(2), r.nextInt(10))
    else r.nextInt(6) match
      case 0 => Prog.Seq(gen(r, depth - 1), gen(r, depth - 1))
      case 1 => Prog.Add(gen(r, depth - 1), gen(r, depth - 1))
      case 2 => Prog.Writer(r.nextInt(2), gen(r, depth - 1))
      case 3 => Prog.State(r.nextInt(2), r.nextInt(10), gen(r, depth - 1))
      case 4 => Prog.Throws(r.nextInt(2), gen(r, depth - 1))
      case _ => Prog.Choose(gen(r, depth - 1))

  /** the program in the monad: every value an Int, a handler's answer folded into one */
  def build(p: Prog, m: Monad): Any = p match
    case Prog.Pure(n) => m.pure(n)
    case Prog.Tell(e, n) => m.map(m.perform(okay.std.Tell(n, writerE(e))), _ => n)
    case Prog.Get(e) => m.perform(okay.std.Get[Int](stateE(e)))
    case Prog.Put(e, n) => m.map(m.perform(okay.std.Put(n, stateE(e))), _ => n)
    case Prog.Among(ns) => m.perform(okay.std.Among(ns))
    case Prog.Raise(e, n) => m.perform(okay.std.Raise(n, throwsE(e)))
    case Prog.Seq(a, b) => m.flatMap(build(a, m), _ => build(b, m))
    case Prog.Add(a, b) => m.flatMap(build(a, m), x => m.map(build(b, m), y => x.asInstanceOf[Int] + y.asInstanceOf[Int]))
    case Prog.Writer(e, body) => m.map(m.handle(Handlers.writer(writerE(e)), mm => build(body, mm)), {
      case (log: List[Any] @unchecked, a) => a.asInstanceOf[Int] * 100 + log.map(_.asInstanceOf[Int]).sum
    })
    case Prog.State(e, s0, body) => m.map(m.handle(Handlers.state(stateE(e), s0), mm => build(body, mm)), {
      case (s, a) => a.asInstanceOf[Int] * 10 + s.asInstanceOf[Int]
    })
    case Prog.Throws(e, body) => m.map(m.handle(Handlers.throws(throwsE(e)), mm => build(body, mm)), {
      case Right(a) => a.asInstanceOf[Int]
      case Left(err) => -1 - err.asInstanceOf[Int]
    })
    case Prog.Choose(body) => m.map(m.handle(Handlers.choose, mm => build(body, mm)), xs => xs.asInstanceOf[List[Int]].sum)

  def outcome(p: Prog, m: Monad): Either[String, Any] =
    try Right(Program.run(new Program[Any] { def in(mm: Monad) = build(p, mm) }, m))
    catch case e: IllegalStateException => Left("no handler")

  val monads = List("Steps" -> Steps, "Ref" -> Ref, "zero" -> OnCore.zero, "cont" -> OnCore.cont)

  test("a thousand programs at random: every monad agrees") {
    val r = Random(20261010)
    var differ = List.empty[String]
    var values = 0
    for i <- 1 to 1000 do
      // a handler of each kind around, so most programs come to a value
      val p = Prog.Choose(Prog.Throws(0, Prog.Throws(1, Prog.State(0, 0, Prog.State(1, 0, Prog.Writer(0, Prog.Writer(1, gen(r, 6))))))))
      val outs = monads.map((n, m) => n -> outcome(p, m))
      if outs.head._2.isRight then values += 1
      if outs.map(_._2).distinct.size > 1 then differ = s"#$i $p: ${outs.mkString(", ")}" :: differ
    assert(differ.isEmpty, differ.take(3).mkString("\n"))
    // every handler in force, but a raise's own: most come to a value
    assert(values > 900, s"$values of 1000 came to a value")
  }
