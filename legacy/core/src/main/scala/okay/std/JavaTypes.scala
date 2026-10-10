package okay.std

import scala.jdk.CollectionConverters.*
import okay.kernel.{Answering, Effect, Fn, Handler, Monad, Op, Prog, Returning}
import okay.kernel.std.{Among, Effects, Final, Logged, Put, Raise, Result, Tell}

// THE STANDARD HANDLERS, THEIR ANSWERS JAVA'S: the API's `Logged`, `Result`, `Final` — okay-java's handlers answer
// the same. A handler is taken by its effect, so a program's operations go to these as to Scala's: only the answer
// differs

/** the handlers, their answers Java's */
object JavaHandlers:
  def writer[W, A](e: Effect): Handler[A, Logged[W, A]] = new Returning[A, Logged[W, A]]:
    def effect: Effect = e
    def answer(a: A): Logged[W, A] = Logged.of(a)
    def apply[X](op: Op[X], k: Fn[X, Prog[Logged[W, A]]], m: Monad): Prog[Logged[W, A]] = op match
      case t: Tell[?] => m.map(k(null.asInstanceOf[X]), l => l.told(t.w.asInstanceOf[W]))

  def throws[E, A](e: Effect): Handler[A, Result[E, A]] = new Returning[A, Result[E, A]]:
    def effect: Effect = e
    def answer(a: A): Result[E, A] = Result.ok(a)
    def apply[X](op: Op[X], k: Fn[X, Prog[Result[E, A]]], m: Monad): Prog[Result[E, A]] = op match
      case r: Raise[?, ?] => m.pure(Result.failed(r.error.asInstanceOf[E]))

  def state[S, A](e: Effect, s0: S): Handler[A, Final[S, A]] = new Answering[A, Final[S, A]] with Returning[A, Final[S, A]]:
    private var s: S = s0
    def effect: Effect = e
    def answer(a: A): Final[S, A] = Final(s, a)
    def value[X](op: Op[X]): X = op match
      case p: Put[?] => s = p.s.asInstanceOf[S]; null.asInstanceOf[X]
      case _ => s.asInstanceOf[X]

  /** every value, a `java.util.List` */
  def choose[A]: Handler[A, java.util.List[A]] = new Returning[A, java.util.List[A]]:
    def effect: Effect = Effects.CHOOSE
    def answer(a: A): java.util.List[A] = List(a).asJava
    def apply[X](op: Op[X], k: Fn[X, Prog[java.util.List[A]]], m: Monad): Prog[java.util.List[A]] = op match
      case c: Among[X @unchecked] =>
        val as = c.as
        def from(i: Int, acc: List[A]): Prog[java.util.List[A]] =
          if i == as.size then m.pure(acc.asJava)
          else m.flatMap(k(as.get(i)), r => from(i + 1, acc ++ r.asScala))
        from(0, Nil)

  /** the first value, a `java.util.Optional` */
  def first[A]: Handler[A, java.util.Optional[A]] = new Returning[A, java.util.Optional[A]]:
    def effect: Effect = Effects.CHOOSE
    def answer(a: A): java.util.Optional[A] = java.util.Optional.of(a)
    def apply[X](op: Op[X], k: Fn[X, Prog[java.util.Optional[A]]], m: Monad): Prog[java.util.Optional[A]] = op match
      case c: Among[X @unchecked] =>
        val as = c.as
        def from(i: Int): Prog[java.util.Optional[A]] =
          if i == as.size then m.pure(java.util.Optional.empty[A]())
          else m.flatMap(k(as.get(i)), r => if r.isPresent then m.pure(r) else from(i + 1))
        from(0)
