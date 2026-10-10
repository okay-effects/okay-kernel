package okay

import okay.kernel.{Effect, Fn, Handler, Monad, Op, Program, Steps}

/**
 * ONE DELIMITER, THE ANSWER TYPE MODIFIED, STATICALLY: Materzok–Biernacki's shift0 and reset0, a program's answer
 * moving from `I` to `O` in its type — on the kernel, a view of a `Program`: every reset0 a handler of one effect,
 * so a shift0 reaches the nearest. Typed as okay-java's `Cont0`
 */
final class Cont0[I, O, A] private[okay] (private[okay] val p: Program[A]):
  def flatMap[I2, B](f: A => Cont0[I2, I, B]): Cont0[I2, O, B] =
    Cont0(m => m.flatMap(p.in(m), a => f(a.asInstanceOf[A]).p.in(m)))
  def map[B](f: A => B): Cont0[I, O, B] = Cont0(m => m.map(p.in(m), a => f(a.asInstanceOf[A])))

object Cont0:
  private val effect: Effect = Effect.of("Reset0")
  private final case class Shift0Op(f: Fn[Fn[Any, Any], Program[?]]) extends Op[Any]:
    def effect: Effect = Cont0.effect

  private def delimiter(back: Any => Program[?]): Handler = new Handler:
    def effect: Effect = Cont0.effect
    def ret(a: Any, m: Monad): Any = back(a).in(m)
    def apply(op: Op[?], k: Fn[Any, Any], m: Monad): Any = op match
      case Shift0Op(f) => f(k).in(m)

  def pure[S, A](a: A): Cont0[S, S, A] = Cont0(m => m.pure(a))
  /** the rest up to the nearest delimiter, the delimiter included, to `f`, run in its place: its answer `O` */
  def shift0[I1, O1, X1, O, A](f: (A => Cont0[I1, O1, X1]) => O): Cont0[Cont0[I1, O1, X1], O, A] =
    Cont0(m => m.perform(Shift0Op(k => f(a => Cont0(_ => k(a))).asInstanceOf[Cont0[?, ?, ?]].p)))
  /** `ret $ body`: `body` under a delimiter, its value to `ret` */
  def dollar[I, I2, O2, B, A](ret: A => I, body: Cont0[I, Cont0[I2, O2, B], A]): Cont0[I2, O2, B] =
    Cont0(m => m.handle(delimiter(a => ret(a.asInstanceOf[A]).asInstanceOf[Cont0[?, ?, ?]].p), body.p))
  /** `body` under a delimiter, its value the answer */
  def reset0[J, I2, O2, B, A](body: Cont0[Cont0[J, J, A], Cont0[I2, O2, B], A]): Cont0[I2, O2, B] =
    Cont0(m => m.handle(delimiter(a => (pm: Monad) => pm.pure(a)), body.p))
  /** a program closed, run: its answer */
  def value[A](c: Cont0[A, A, A], m: Monad = Steps): A = Program.run(c.p, m)
