package okay.std

import okay.{!, %, Eff}
import okay.kernel.{Answering, Effect, Fn, Handler, Monad, Op, Thunk}

// THE STANDARD EFFECTS: their operations data, their handlers values — the same for every language (Java builds
// `new Tell<>(w)`, takes `Handlers.writer()`); Scala 3 has them typed by its rows besides

/** the effects, by identity */
object Effects:
  val writer: Effect = Effect("Writer")
  val async: Effect = Effect("Async")
  val throws: Effect = Effect("Throws")

/** a log: `w` in front of the rest's */
final case class Tell[W](w: W) extends Op[Unit]:
  def effect: Effect = Effects.writer

/** a computation awaited — here answered in place, run where the program runs (the kernel's first step) */
final case class Await[A](c: Thunk[A]) extends Op[A]:
  def effect: Effect = Effects.async

/** a failure: the rest dropped */
final case class Raise[E](e: E) extends Op[Nothing]:
  def effect: Effect = Effects.throws

/** the handlers, values: any language installs them on any monad */
object Handlers:
  /** the body's value with its log, each tell in front of the rest's */
  val writer: Handler = new Handler:
    def effect: Effect = Effects.writer
    def ret(a: Any, m: Monad): Any = m.pure((Nil, a))
    def apply(op: Op[?], k: Fn[Any, Any], m: Monad): Any = op match
      case Tell(w) => m.map(k(()), { case (log: List[Any] @unchecked, a) => (w :: log, a) })

  val async: Handler = new Answering:
    def effect: Effect = Effects.async
    def ret(a: Any, m: Monad): Any = m.pure(a)
    def value(op: Op[?]): Any = op match
      case Await(c) => c.get()

  /** the body's value a `Right`, a raise's a `Left` */
  val throws: Handler = new Handler:
    def effect: Effect = Effects.throws
    def ret(a: Any, m: Monad): Any = m.pure(Right(a))
    def apply(op: Op[?], k: Fn[Any, Any], m: Monad): Any = op match
      case Raise(e) => m.pure(Left(e))

// SCALA 3'S TYPED VIEW: each effect a row's element

trait Writer[W]
trait Async
trait Throws[E]

def tell[W](w: W): Unit ! Writer % W = Eff.perform(Tell(w))
def writer[W, R, A](p: A ! (Writer[W] & R)): (List[W], A) ! R = Eff.handle(Handlers.writer, p)

def await[A](c: => A): A ! Async = Eff.perform(Await(() => c))
def async[R, A](p: A ! (Async & R)): A ! R = Eff.handle(Handlers.async, p)

def raise[E](e: E): Nothing ! Throws % E = Eff.perform(Raise(e))
def attempt[E, R, A](p: A ! (Throws[E] & R)): Either[E, A] ! R = Eff.handle(Handlers.throws, p)
