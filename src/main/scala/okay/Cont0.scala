package okay

import okay.kernel.{Effect, Fn, Handler, Monad, Op, Prog, Program, Steps}

/**
 * ONE DELIMITER, THE ANSWER TYPE MODIFIED, STATICALLY: Materzok–Biernacki's shift0 and reset0, a program's answer
 * moving from `I` to `O` in its type — on the kernel, a view of a `Program`: every reset0 a handler of one effect,
 * so a shift0 reaches the nearest. Typed as okay-java's `Cont0`; the answers' types are this view's, a delimiter's
 * erased to `Any` inside it
 */
final class Cont0[I, O, A] private[okay] (private[okay] val p: Program[A]):
  def flatMap[I2, B](f: Fn[A, Cont0[I2, I, B]]): Cont0[I2, O, B] =
    Cont0(m => m.flatMap(p.in(m), a => f(a).p.in(m)))
  def map[B](f: Fn[A, B]): Cont0[I, O, B] = Cont0(m => m.map(p.in(m), f))

object Cont0:
  private val effect: Effect = Effect.of("Reset0")
  private final case class Shift0Op[A](f: Fn[Fn[A, Prog[Any]], Program[Any]]) extends Op[A]:
    def effect: Effect = Cont0.effect

  private def delimiter[A](back: A => Program[Any]): Handler[A, Any] = new Handler[A, Any]:
    def effect: Effect = Cont0.effect
    def ret(a: A, m: Monad): Prog[Any] = back(a).in(m)
    def apply[X](op: Op[X], k: Fn[X, Prog[Any]], m: Monad): Prog[Any] = op match
      case s: Shift0Op[X @unchecked] => s.f(k).in(m)

  private def inner[A](c: Cont0[?, ?, ?]): Program[A] = c.p.asInstanceOf[Program[A]]

  def pure[S, A](a: A): Cont0[S, S, A] = Cont0(m => m.pure(a))
  /** the rest up to the nearest delimiter, the delimiter included, to `f`, run in its place: its answer `O` */
  def shift0[I1, O1, X1, O, A](f: Fn[Fn[A, Cont0[I1, O1, X1]], O]): Cont0[Cont0[I1, O1, X1], O, A] =
    Cont0(m => m.perform(Shift0Op[A](k => inner(f(a => Cont0(_ => k(a).asInstanceOf[Prog[X1]])).asInstanceOf[Cont0[?, ?, ?]]))))
  /** `ret $ body`: `body` under a delimiter, its value to `ret` */
  def dollar[I, I2, O2, B, A](ret: Fn[A, I], body: Cont0[I, Cont0[I2, O2, B], A]): Cont0[I2, O2, B] =
    Cont0(m => m.handle(delimiter[A](a => inner(ret(a).asInstanceOf[Cont0[?, ?, ?]])), body.p).asInstanceOf[Prog[B]])
  /** `body` under a delimiter, its value the answer */
  def reset0[J, I2, O2, B, A](body: Cont0[Cont0[J, J, A], Cont0[I2, O2, B], A]): Cont0[I2, O2, B] =
    Cont0(m => m.handle(delimiter[A](a => (pm: Monad) => pm.pure[Any](a)), body.p).asInstanceOf[Prog[B]])
  /** a program closed, run: its answer */
  def value[A](c: Cont0[A, A, A]): A = Program.run(c.p, Steps)
  def value[A](c: Cont0[A, A, A], m: Monad): A = Program.run(c.p, m)
  /** a closed program as a kernel `Program`: under any handler, in any language */
  def program[A](c: Cont0[A, A, A]): Program[A] = c.p
