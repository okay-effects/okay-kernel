package okay

import okay.kernel.{Effect, Fn, Monad, Op, Prog}

/**
 * A HANDLER, TYPED BY THE ROW — itself the kernel's `Handler[A, R]`, so installed on any monad, from any language —
 * its clauses written on Scala 3's programs: `E` the row's element it takes off, `A` the body's value, `R` its
 * answer, `Rest` the effects its clauses need (they run outside it, in its place). `k` is typed by the operation's own
 * value: `apply[X](op: Op[X], k: X => R ! Rest)` — matching `case Tell(w, _)` tells Scala `X` is `Unit`.
 */
abstract class Handler[E, A, R, Rest](val effect: Effect) extends okay.kernel.Handler[A, R]:
  /** the body's value to the answer */
  def ret(a: A): R ! Rest
  /** an operation and the rest, to the answer: `k(x)` the rest under this handler again (deep), any number of times */
  def apply[X](op: Op[X], k: X => R ! Rest): R ! Rest

  /** `p` under this handler: its effect taken off, the clauses' effects added */
  def handle[O](p: A ! (E & O)): R ! (O & Rest) = Eff.handle(this, p)

  // the kernel's: the typed clauses, built in the monad given
  final def ret(a: A, m: Monad): Prog[R] = ret(a).in(m)
  def apply[X](op: Op[X], k: Fn[X, Prog[R]], m: Monad): Prog[R] = apply[X](op, (x: X) => Eff.lift[Rest, R](k(x))).in(m)

/** A TAIL-RESUMPTIVE HANDLER, typed by the row: each clause `k(value(op))` — answered in place, no capture.
 * `value[X](op: Op[X]): X` the kernel's own, typed by the operation */
abstract class Answering[E, A, R, Rest](effect: Effect)
    extends Handler[E, A, R, Rest](effect) with okay.kernel.Answering[A, R]:
  def apply[X](op: Op[X], k: X => R ! Rest): R ! Rest = k(value(op))
  override def apply[X](op: Op[X], k: Fn[X, Prog[R]], m: Monad): Prog[R] = k(value(op))
