package okay.kernel

import okay.{!, %, +, Eff, Handler as Typed, Answering as TypedAnswering}
import okay.std.*

// ONE'S OWN EFFECTS, THEIR HANDLERS TYPED: the row's element, the operations, the handler

/** asking a number, answered in place */
trait Asks
final case class Ask() extends Op[Int]:
  def effect: Effect = TestTyped.asks
def ask: Int ! Asks = Eff.perform(Ask())

/** a log of one's own: each note in front of the rest's */
trait Notes
final case class Note(s: String) extends Op[Unit]:
  def effect: Effect = TestTyped.notes
def note(s: String): Unit ! Notes = Eff.perform(Note(s))

/** a failure, recovered by a default — its handler's clause telling the outer log it did */
trait Fails
final case class Fail() extends Op[Nothing]:
  def effect: Effect = TestTyped.fails
def failing: Nothing ! Fails = Eff.perform(Fail())

/** a coin, both sides tried */
trait Coins
final case class Flip() extends Op[Boolean]:
  def effect: Effect = TestTyped.coins
def flip: Boolean ! Coins = Eff.perform(Flip())

object TestTyped:
  val asks: Effect = Effect.of("Asks")
  val notes: Effect = Effect.of("Notes")
  val fails: Effect = Effect.of("Fails")
  val coins: Effect = Effect.of("Coins")

  def asking[A](n: Int): TypedAnswering[Asks, A, A, Any] = new TypedAnswering[Asks, A, A, Any](asks):
    def ret(a: A): A ! Any = Eff.pure(a)
    def value[X](op: Op[X]): X = op match
      case Ask() => n

  def noting[A]: Typed[Notes, A, (List[String], A), Any] = new Typed[Notes, A, (List[String], A), Any](notes):
    def ret(a: A): (List[String], A) ! Any = Eff.pure((Nil, a))
    def apply[X](op: Op[X], k: X => (List[String], A) ! Any): (List[String], A) ! Any = op match
      case Note(s) => k(()).map((log, a) => (s :: log, a))

  /** its clause needs a log of Strings, outside it: the row says so */
  def recovering(default: Int): Typed[Fails, Int, Int, Writer[String]] = new Typed[Fails, Int, Int, Writer[String]](fails):
    def ret(a: Int): Int ! Writer[String] = Eff.pure(a)
    def apply[X](op: Op[X], k: X => Int ! Writer[String]): Int ! Writer[String] = op match
      case Fail() => tell("recovered").map(_ => default)

  def allSides[A]: Typed[Coins, A, List[A], Any] = new Typed[Coins, A, List[A], Any](coins):
    def ret(a: A): List[A] ! Any = Eff.pure(List(a))
    def apply[X](op: Op[X], k: X => List[A] ! Any): List[A] ! Any = op match
      case Flip() => k(true).flatMap(a => k(false).map(b => a ++ b))

/** typed handlers, written once, on every monad */
abstract class TestTypedOn(m: Monad) extends munit.FunSuite:
  import TestTyped.*
  def run[A](p: A ! Any): A = Eff.run(p, m)

  test("an answering handler, typed: its value by the operation") {
    assertEquals(run(asking[Int](41).handle(ask.map(_ + 1))), 42)
  }
  test("a handler with k, typed: its clause on k's answer") {
    assertEquals(run(noting[Int].handle(note("a").andThen(note("b")).map(_ => 7))), (List("a", "b"), 7))
  }
  test("a clause needing an effect outside: the row says it, a handler of it around") {
    val q: Int ! Fails + Asks = ask.flatMap(n => if n > 10 then failing else Eff.pure(n))
    assertEquals(run(writer(recovering(-1).handle(asking[Int](5).handle(q)))), (Nil, 5))
    assertEquals(run(writer(recovering(-1).handle(asking[Int](50).handle(q)))), (List("recovered"), -1))
  }
  test("multi-shot, typed: both sides of each coin") {
    val q: (Boolean, Boolean) ! Coins = flip.flatMap(a => flip.map(b => (a, b)))
    assertEquals(run(allSides[(Boolean, Boolean)].handle(q)), List((true, true), (true, false), (false, true), (false, false)))
  }

class TestTypedSteps extends TestTypedOn(Steps)
class TestTypedRef extends TestTypedOn(Ref)
