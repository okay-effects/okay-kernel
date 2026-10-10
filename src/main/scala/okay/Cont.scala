package okay

import okay.kernel.{Effect, Fn, Handler, Monad, Op, Prog, Program, Steps}

/**
 * SHIFT AND RESET, THE ANSWER TYPE MODIFIED, STATICALLY: Danvy–Filinski's, typed as Asai–Kameyama's — a program of
 * value `A` which, its rest up to the nearest reset answering `I`, answers `O`. On the kernel, a view of a `Program`:
 * every reset a handler of one effect, so a shift reaches the nearest; its clause runs under the reset again. Its
 * signatures plain generics and `Fn`: Java writes it as Scala does (Scala's lambdas are `Fn`s)
 */
final class Cont[I, O, A] private[okay] (private[okay] val p: Program[A]):
  def flatMap[I2, B](f: Fn[A, Cont[I2, I, B]]): Cont[I2, O, B] = Cont(m => m.flatMap(p.in(m), a => f(a).p.in(m)))
  def map[B](f: Fn[A, B]): Cont[I, O, B] = Cont(m => m.map(p.in(m), f))

object Cont:
  private val effect: Effect = Effect.of("Reset")
  private final case class ShiftOp[A](f: Fn[Fn[A, Prog[Any]], Program[Any]]) extends Op[A]:
    def effect: Effect = Cont.effect

  private val delimiter: Handler[Any, Any] = new Handler[Any, Any]:
    def effect: Effect = Cont.effect
    def ret(a: Any, m: Monad): Prog[Any] = m.pure(a)
    def apply[X](op: Op[X], k: Fn[X, Prog[Any]], m: Monad): Prog[Any] = op match
      case s: ShiftOp[X @unchecked] => m.handle(this, s.f(k))

  def pure[S, A](a: A): Cont[S, S, A] = Cont(m => m.pure(a))

  /** `shift(k => body)`: `k` the rest up to the nearest reset, the reset included, answering `I` at the hole; the
   * body under the reset again, at its final answer `O` — the answer moved from `I` to `O` */
  def shift[I, O, A](f: Fn[Fn[A, Cont[O, O, I]], Cont[O, O, O]]): Cont[I, O, A] =
    Cont(m => m.perform(ShiftOp[A](k => f(a => Cont(_ => k(a).asInstanceOf[Prog[I]])).p.asInstanceOf[Program[Any]])))

  /** `reset(body)`: the body at the answer `S`, its value; the reset answers what the body moved it to, `O` */
  def reset[T, S, O](body: Cont[S, O, S]): Cont[T, T, O] =
    Cont(m => m.handle(delimiter, body.p.asInstanceOf[Program[Any]]).asInstanceOf[Prog[O]])

  /** a continuation's answer, now: `k(x)` holds its reset, so it runs on its own — Asai–Kameyama's pure `k`, on the
   * machine of steps or the monad given */
  def eval[T, I](c: Cont[T, T, I]): I = Program.run(c.p, Steps)
  def eval[T, I](c: Cont[T, T, I], m: Monad): I = Program.run(c.p, m)

  /** a program closed, run: its answer */
  def value[A](c: Cont[A, A, A]): A = Program.run(c.p, Steps)
  def value[A](c: Cont[A, A, A], m: Monad): A = Program.run(c.p, m)
  /** a closed program as a kernel `Program`: under any handler, in any language */
  def program[A](c: Cont[A, A, A]): Program[A] = c.p
