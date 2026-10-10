package okay.std

import okay.{!, Eff}
import okay.kernel.{Effect, Fn, Handler, Monad, Op, Prog, Program}
import okay.kernel.std.{Shift, Shift0}

/**
 * DELIMITED CONTROL ON THE KERNEL: a reset is a handler of an effect fresh each run — its prompt — and a shift an
 * operation of it. No primitive of the kernel's own: `Effect(name)` is generative, so a prompt is an effect no other
 * handler takes. `shift0`'s clause runs outside the delimiter (Materzok–Biernacki), `shift`'s under it again
 * (Danvy–Filinski)
 */
final class Prompt[R] private[std] (private[std] val effect: Effect):
  /** the rest up to this prompt's delimiter, the delimiter included, to the clause, run in its place, outside */
  def shift0[A](f: (A => R ! Any) => R ! Any): A ! Ctl =
    Eff.perform(Shift0[A, R](effect, k => f(a => Eff.lift(k(a)))))
  /** the same, the clause under the delimiter again */
  def shift[A](f: (A => R ! Any) => R ! Ctl): A ! Ctl =
    Eff.perform(Shift[A, R](effect, k => f(a => Eff.lift(k(a)))))

/** the row's element of a prompt's operations: any prompt's — the prompt's own found by its identity at run time */
trait Ctl

/** a delimiter of a fresh prompt, its body's value the answer */
def reset[R, X](body: Prompt[R] => R ! (Ctl & X)): R ! X =
  Eff.from[X, R] { m =>
    val p = Prompt[R](Effect("Prompt"))
    m.handle(delimiter[R](p.effect), body(p))
  }

private[okay] def delimiter[R](e: Effect): Handler[R, R] = new Handler[R, R]:
  def effect: Effect = e
  def ret(a: R, m: Monad): Prog[R] = m.pure(a)
  def apply[X](op: Op[X], k: Fn[X, Prog[R]], m: Monad): Prog[R] = op match
    case s: Shift0[X @unchecked, R @unchecked] => s.f.apply(k).in(m)
    case s: Shift[X @unchecked, R @unchecked] => m.handle(this, s.f.apply(k))
