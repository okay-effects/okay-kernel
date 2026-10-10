package okay.std

import okay.{!, %, Eff, Tag}
import okay.kernel.{Answering, Effect, Fn, Handler, Monad, Op, Prog, Program, Returning, Sink, Thunk}
import okay.kernel.std.{Among, Await, Callback, Effects, Get, Put, Raise, Tell}

// THE STANDARD EFFECTS: their data — effects, operations, answers — the API's, `okay.kernel.std`, one class each for
// every implementation (okay-java's handlers take this one's operations, and back); their handlers values, typed;
// Scala 3 has them by its rows too. An operation of value `Void` answered `null`: Java's continuations cast to `Void`

/** the handlers, typed values: any language installs them on any monad */
object Handlers:
  // the handlers of no state made, by effect: a map replaced whole when one is added, read with no lock
  @volatile private var made = Map.empty[(Effect, Int), AnyRef]
  private def once[H <: AnyRef](e: Effect, kind: Int, make: => H): H =
    made.get((e, kind)) match
      case Some(h) => h.asInstanceOf[H]
      case None => synchronized {
        made.get((e, kind)) match
          case Some(h) => h.asInstanceOf[H]
          case None => val h = make; made = made.updated((e, kind), h); h
      }

  /** the body's value with its log, each tell in front of the rest's */
  def writer[W, A](e: Effect): Handler[A, (List[W], A)] =
    once(e, 0, writerOf[Any, Any](e)).asInstanceOf[Handler[A, (List[W], A)]]
  private def writerOf[W, A](e: Effect): Handler[A, (List[W], A)] = new Returning[A, (List[W], A)]:
    def effect: Effect = e
    def answer(a: A): (List[W], A) = (Nil, a)
    def apply[X](op: Op[X], k: Fn[X, Prog[(List[W], A)]], m: Monad): Prog[(List[W], A)] = op match
      case t: Tell[?] => m.map(k(null.asInstanceOf[X]), (log, a) => (t.w.asInstanceOf[W] :: log, a))

  /** the body's value a `Right`, a raise's a `Left` */
  def throws[E, A](e: Effect): Handler[A, Either[E, A]] =
    once(e, 1, throwsOf[Any, Any](e)).asInstanceOf[Handler[A, Either[E, A]]]
  private def throwsOf[E, A](e: Effect): Handler[A, Either[E, A]] = new Returning[A, Either[E, A]]:
    def effect: Effect = e
    def answer(a: A): Either[E, A] = Right(a)
    def apply[X](op: Op[X], k: Fn[X, Prog[Either[E, A]]], m: Monad): Prog[Either[E, A]] = op match
      case r: Raise[?, ?] => m.pure(Left(r.error.asInstanceOf[E]))

  /** a cell, one a run of the handler: the final state with the value */
  def state[S, A](e: Effect, s0: S): Handler[A, (S, A)] = new Answering[A, (S, A)] with Returning[A, (S, A)]:
    private var s: S = s0
    def effect: Effect = e
    def answer(a: A): (S, A) = (s, a)
    def value[X](op: Op[X]): X = op match
      case p: Put[?] => s = p.s.asInstanceOf[S]; null.asInstanceOf[X]
      case _ => s.asInstanceOf[X]

  /** every value the body comes to, a list, one a path through its choices */
  def choose[A]: Handler[A, List[A]] = chooseAll.asInstanceOf[Handler[A, List[A]]]
  private val chooseAll: Handler[Any, List[Any]] = new Returning[Any, List[Any]]:
    def effect: Effect = Effects.CHOOSE
    def answer(a: Any): List[Any] = List(a)
    def apply[X](op: Op[X], k: Fn[X, Prog[List[Any]]], m: Monad): Prog[List[Any]] = op match
      case c: Among[X @unchecked] =>
        val as = c.as
        def from(i: Int, acc: List[Any]): Prog[List[Any]] =
          if i == as.size then m.pure(acc)
          else m.flatMap(k(as.get(i)), r => from(i + 1, acc ++ r))
        from(0, Nil)

  /** the first value the body comes to, its choices tried in order, the rest not tried */
  def first[A]: Handler[A, Option[A]] = chooseFirst.asInstanceOf[Handler[A, Option[A]]]
  private val chooseFirst: Handler[Any, Option[Any]] = new Returning[Any, Option[Any]]:
    def effect: Effect = Effects.CHOOSE
    def answer(a: Any): Option[Any] = Some(a)
    def apply[X](op: Op[X], k: Fn[X, Prog[Option[Any]]], m: Monad): Prog[Option[Any]] = op match
      case c: Among[X @unchecked] =>
        val as = c.as
        def from(i: Int): Prog[Option[Any]] =
          if i == as.size then m.pure(None)
          else m.flatMap(k(as.get(i)), r => if r.isDefined then m.pure(r) else from(i + 1))
        from(0)

  /** awaits answered at once only — a callback called by its registration: the run goes on there. One called later
   * needs the run's top, `Async.start` */
  def async[A]: Handler[A, A] = asyncNow.asInstanceOf[Handler[A, A]]
  private val asyncNow: Handler[Any, Any] = new Handler[Any, Any]:
    def effect: Effect = Effects.ASYNC
    def ret(a: Any, m: Monad): Prog[Any] = m.pure(a)
    def apply[X](op: Op[X], k: Fn[X, Prog[Any]], m: Monad): Prog[Any] = op match
      case w: Await[X @unchecked] =>
        val cell = Async.Cell[X]()
        w.register.accept(cell)
        if !cell.answered then throw IllegalStateException("an await answered later, under no Async.start")
        cell.failure match
          case null => k(cell.value)
          case e => throw e

/** ASYNC AT THE TOP: a run that awaits callbacks called later, from any thread — the run stops at the await, the
 * callback goes on with it, a run of its own, to the end: the value to `done`. Its handler the outermost: the others
 * inside the program */
object Async:
  private[std] final class Cell[X] extends Callback[X]:
    @volatile var answered = false
    @volatile var registered = false
    var value: X = null.asInstanceOf[X]
    var failure: Throwable | Null = null
    @volatile var later: Callback[X] | Null = null
    def ok(a: X): Unit = synchronized {
      if registered then later.nn.ok(a) else { value = a; answered = true }
    }
    def fail(e: Throwable): Unit = synchronized {
      if registered then later.nn.fail(e) else { failure = e; answered = true }
    }

  /** the run's answer: its value, or nothing yet — suspended at an await */
  private enum Answer[+A]:
    case Done(a: A)
    case Suspended

  def start[A](p: Program[A], m: Monad, done: Sink[A], failed: Sink[Throwable]): Unit =
    def go(c: Prog[Answer[A]]): Unit =
      val r = try m.run(c) catch { case e: Throwable => failed.accept(e); return }
      r match
        case Answer.Done(a) => done.accept(a)
        case Answer.Suspended =>
    lazy val top: Handler[A, Answer[A]] = new Handler[A, Answer[A]]:
      def effect: Effect = Effects.ASYNC
      def ret(a: A, mm: Monad): Prog[Answer[A]] = mm.pure(Answer.Done(a))
      def apply[X](op: Op[X], k: Fn[X, Prog[Answer[A]]], mm: Monad): Prog[Answer[A]] = op match
        case w: Await[X @unchecked] =>
          val cell = Cell[X]()
          w.register.accept(cell)
          cell.synchronized {
            if cell.answered then
              cell.failure match
                case null => k(cell.value)
                case e => throw e
            else
              // answered later: from the callback, the rest a run of its own
              cell.later = new Callback[X]:
                def ok(a: X): Unit = go(k(a))
                def fail(e: Throwable): Unit = failed.accept(e)
              cell.registered = true
              mm.pure(Answer.Suspended)
          }
    go(m.handle(top, p))

// SCALA 3'S TYPED VIEW: each effect a row's element

trait Writer[W]
trait Throws[E]
trait State[S]
trait Choose
trait Async

private def writerE(t: Tag[?]): Effect = t.effect(Tag.Writer, Effects.writer)
private def throwsE(t: Tag[?]): Effect = t.effect(Tag.Throws, Effects.throwsOf)
private def stateE(t: Tag[?]): Effect = t.effect(Tag.State, Effects.state)

/** a `Void` operation's `null`, Scala's `()` */
private val unit: Void => Unit = _ => ()

def tell[W](w: W)(using t: Tag[W]): Unit ! Writer % W = Eff.perform[Writer[W], Void](Tell(w, writerE(t))).map(unit)
def writer[W, R, A](p: A ! (Writer[W] & R))(using t: Tag[W]): (List[W], A) ! R =
  Eff.handle(t.handler(Tag.Writer, Handlers.writer[W, A](writerE(t))), p)

def raise[E](e: E)(using t: Tag[E]): Nothing ! Throws % E = Eff.perform(Raise[E, Nothing](e, throwsE(t)))
def attempt[E, R, A](p: A ! (Throws[E] & R))(using t: Tag[E]): Either[E, A] ! R =
  Eff.handle(t.handler(Tag.Throws, Handlers.throws[E, A](throwsE(t))), p)

def get[S](using t: Tag[S]): S ! State % S = Eff.perform(Get[S](stateE(t)))
def put[S](s: S)(using t: Tag[S]): Unit ! State % S = Eff.perform[State[S], Void](Put(s, stateE(t))).map(unit)
/** a state of a cell, one a run */
def state[S, R, A](s0: S, p: A ! (State[S] & R))(using t: Tag[S]): (S, A) ! R =
  Eff.from[R, (S, A)](m => m.handle(Handlers.state[S, A](stateE(t), s0), p))

def among[A](as: Seq[A]): A ! Choose =
  val l = java.util.ArrayList[A](as.size)
  as.foreach(l.add)
  Eff.perform(Among(l))
def choose[R, A](p: A ! (Choose & R)): List[A] ! R = Eff.handle(Handlers.choose[A], p)
/** the first value the body comes to */
def first[R, A](p: A ! (Choose & R)): Option[A] ! R = Eff.handle(Handlers.first[A], p)
def fail[A]: A ! Choose = among(Seq.empty[A])

/** a callback's value, awaited */
def await[A](register: Callback[A] => Unit): A ! Async = Eff.perform(Await[A](c => register(c)))
/** a value computed now, as an await answered at once */
def awaitNow[A](c: => A): A ! Async = await(cb => cb.ok(c))
/** awaits answered at once only, inside a program */
def async[R, A](p: A ! (Async & R)): A ! R = Eff.handle(Handlers.async[A], p)
