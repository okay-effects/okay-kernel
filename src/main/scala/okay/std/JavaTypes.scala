package okay.std

import scala.jdk.CollectionConverters.*
import okay.kernel.{Answering, Effect, Fn, Handler, Monad, Op, Prog}

// THE STANDARD HANDLERS FOR JAVA: the same effects, the same operations — their answers Java's types. A handler is
// taken by its effect, so a program's operations go to these as to Scala's: only the answer differs

/** a log and the value: `log()` a `java.util.List`, first told first */
final class Logged[W, A] private[okay] (private[okay] val items: List[W], val value: A):
  def log: java.util.List[W] = items.asJava
  override def equals(o: Any): Boolean = o match
    case l: Logged[?, ?] => l.items == items && l.value == value
    case _ => false
  override def hashCode: Int = (items, value).##
  override def toString: String = s"Logged($items, $value)"

/** the final state and the value */
final case class Final[S, A](state: S, value: A)

/** the value, or the failure raised */
sealed trait Result[E, A]
object Result:
  final case class Ok[E, A](value: A) extends Result[E, A]
  final case class Failed[E, A](error: E) extends Result[E, A]
  def ok[E, A](a: A): Result[E, A] = Ok(a)
  def failed[E, A](e: E): Result[E, A] = Failed(e)

/** the handlers, their answers Java's */
object JavaHandlers:
  def writer[W, A](e: Effect): Handler[A, Logged[W, A]] = new Handler[A, Logged[W, A]]:
    def effect: Effect = e
    def ret(a: A, m: Monad): Prog[Logged[W, A]] = m.pure(Logged(Nil, a))
    def apply[X](op: Op[X], k: Fn[X, Prog[Logged[W, A]]], m: Monad): Prog[Logged[W, A]] = op match
      case Tell(w, _) => m.map(k(()), l => Logged(w.asInstanceOf[W] :: l.items, l.value))

  def throws[E, A](e: Effect): Handler[A, Result[E, A]] = new Handler[A, Result[E, A]]:
    def effect: Effect = e
    def ret(a: A, m: Monad): Prog[Result[E, A]] = m.pure(Result.ok(a))
    def apply[X](op: Op[X], k: Fn[X, Prog[Result[E, A]]], m: Monad): Prog[Result[E, A]] = op match
      case Raise(err, _) => m.pure(Result.failed(err.asInstanceOf[E]))

  def state[S, A](e: Effect, s0: S): Handler[A, Final[S, A]] = new Answering[A, Final[S, A]]:
    private var s: S = s0
    def effect: Effect = e
    def ret(a: A, m: Monad): Prog[Final[S, A]] = m.pure(Final(s, a))
    def value[X](op: Op[X]): X = op match
      case Get(_) => s.asInstanceOf[X]
      case Put(n, _) => s = n.asInstanceOf[S]; ().asInstanceOf[X]

  /** every value, a `java.util.List` */
  def choose[A]: Handler[A, java.util.List[A]] = new Handler[A, java.util.List[A]]:
    def effect: Effect = Effects.choose
    def ret(a: A, m: Monad): Prog[java.util.List[A]] = m.pure(List(a).asJava)
    def apply[X](op: Op[X], k: Fn[X, Prog[java.util.List[A]]], m: Monad): Prog[java.util.List[A]] = op match
      case Among(as) =>
        def from(i: Int, acc: List[A]): Prog[java.util.List[A]] =
          if i == as.length then m.pure(acc.asJava)
          else m.flatMap(k(as(i)), r => from(i + 1, acc ++ r.asScala))
        from(0, Nil)

  /** the first value, a `java.util.Optional` */
  def first[A]: Handler[A, java.util.Optional[A]] = new Handler[A, java.util.Optional[A]]:
    def effect: Effect = Effects.choose
    def ret(a: A, m: Monad): Prog[java.util.Optional[A]] = m.pure(java.util.Optional.of(a))
    def apply[X](op: Op[X], k: Fn[X, Prog[java.util.Optional[A]]], m: Monad): Prog[java.util.Optional[A]] = op match
      case Among(as) =>
        def from(i: Int): Prog[java.util.Optional[A]] =
          if i == as.length then m.pure(java.util.Optional.empty[A]())
          else m.flatMap(k(as(i)), r => if r.isPresent then m.pure(r) else from(i + 1))
        from(0)
