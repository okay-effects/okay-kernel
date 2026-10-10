package okay.std

import okay.{!, Eff}
import okay.kernel.{Effect, Fn, Handler, Monad, Op, Program}

/**
 * DELIMITED CONTROL ON THE KERNEL: a reset is a handler of an effect fresh each run — its prompt — and a shift an
 * operation of it. No primitive of the kernel's own: `Effect(name)` is generative, so a prompt is an effect no other
 * handler takes. `shift0`'s clause runs outside the delimiter (Materzok–Biernacki), `shift`'s under it again
 * (Danvy–Filinski)
 */
final class Prompt[R] private[std] (private[std] val effect: Effect):
  /** the rest up to this prompt's delimiter, the delimiter included, to the clause, run in its place, outside */
  def shift0[A](f: (A => R ! Any) => R ! Any): A ! Ctl =
    Eff.perform(Shift0[A](effect, (k: Fn[Any, Any]) => f(a => Eff.from(m => k(a)))))
  /** the same, the clause under the delimiter again */
  def shift[A](f: (A => R ! Any) => R ! Ctl): A ! Ctl =
    Eff.perform(Shift[A](effect, (k: Fn[Any, Any]) => f(a => Eff.from(m => k(a)))))

/** the row's element of a prompt's operations: any prompt's — the prompt's own found by its identity at run time */
trait Ctl

final case class Shift0[A](effect: Effect, f: Fn[Fn[Any, Any], Program[?]]) extends Op[A]
final case class Shift[A](effect: Effect, f: Fn[Fn[Any, Any], Program[?]]) extends Op[A]

/** a delimiter of a fresh prompt, its body's value the answer */
def reset[R, X](body: Prompt[R] => R ! (Ctl & X)): R ! X =
  Eff.from[X, R] { m =>
    val p = Prompt[R](Effect("Prompt"))
    m.handle(delimiter(p.effect), body(p))
  }

private def delimiter(e: Effect): Handler = new Handler:
  def effect: Effect = e
  def ret(a: Any, m: Monad): Any = m.pure(a)
  def apply(op: Op[?], k: Fn[Any, Any], m: Monad): Any = op match
    case Shift0(_, f) => f(k).in(m)
    case Shift(_, f) => m.handle(this, f(k))
