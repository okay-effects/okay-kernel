package okay

import okay.kernel.{Effect, Fn, Monad, Op}

/**
 * A HANDLER, TYPED — itself a kernel `Handler`, so installed on any monad, from any language — written with Scala 3's
 * types: `E` the row's element it takes off, `A` the body's value, `R` its answer, `Rest` the effects its clauses
 * need (they run outside it, in its place). A clause is given the operation and `k`, typed by the operation's own
 * value: `apply[X](op: Op[X], k: X => R ! Rest)` — matching `case Tell(w, _)` tells Scala `X` is `Unit`.
 */
abstract class Handler[E, A, R, Rest](val effect: Effect) extends okay.kernel.Handler:
  /** the body's value to the answer */
  def ret(a: A): R ! Rest
  /** an operation and the rest, to the answer: `k(x)` the rest under this handler again (deep), any number of times */
  def apply[X](op: Op[X], k: X => R ! Rest): R ! Rest

  /** `p` under this handler: its effect taken off, the clauses' effects added */
  def handle[O](p: A ! (E & O)): R ! (O & Rest) = Eff.handle(this, p)

  // the kernel's: the typed ones, built in the monad given
  final def ret(a: Any, m: Monad): Any = ret(a.asInstanceOf[A]).in(m)
  def apply(op: Op[?], k: Fn[Any, Any], m: Monad): Any =
    apply[Any](op.asInstanceOf[Op[Any]], x => Eff.from[Rest, R](_ => k(x))).in(m)

/** A TAIL-RESUMPTIVE HANDLER, typed: each clause `k(value(op))` — answered in place, no capture */
abstract class Answering[E, A, R, Rest](effect: Effect) extends Handler[E, A, R, Rest](effect) with okay.kernel.Answering:
  /** the operation's value, by the operation alone */
  def answer[X](op: Op[X]): X
  def apply[X](op: Op[X], k: X => R ! Rest): R ! Rest = k(answer(op))
  final def value(op: Op[?]): Any = answer[Any](op.asInstanceOf[Op[Any]])
  // answered in place, as the kernel's: no program of the typed clause built
  override def apply(op: Op[?], k: Fn[Any, Any], m: Monad): Any = k(value(op))
