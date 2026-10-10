package okay.kernel.okayjava

import okay.core.{Eff, Prompt}
import okay.kernel.{Answering, Effect, Fn, Handler, Monad, Op, Prog, Program, Thunk}

/**
 * A MONAD OF THE KERNEL ON OKAY-JAVA: its programs okay-java's `Eff`, run by its machine — the prompts' one. A kernel
 * handler is an `Eff.handle`, its body given a fresh prompt; the context remembers the handlers in force, innermost
 * first, each with its prompt, so an operation goes to the nearest handler of its effect by `shift0` to that prompt —
 * its clause run outside, in the handler's place, `k` the rest with the handler (deep), as the kernel says. An
 * answering handler's operation is no capture: `Eff.sync`, its value read in place. okay-java's levels are types
 * only: here every program is at one level, `Any`.
 */
final class OnJava private (inForce: List[OnJava.InForce]) extends Monad:
  private type E = Eff[Any, Any]
  /** okay-java's program, as the kernel's: a wrapper — one object a node, the adapter's cost */
  private final class W[A](val p: E) extends Prog[A]
  private inline def prog(x: Prog[?]): E = x.asInstanceOf[W[?]].p
  private inline def wrap[A](p: Eff[?, ?]): Prog[A] = W[A](p.asInstanceOf[E])

  def pure[A](a: A): Prog[A] = wrap(Eff.pure[Any, A](a))
  def flatMap[A, B](p: Prog[A], f: Fn[A, Prog[B]]): Prog[B] = wrap(prog(p).flatMap[Any](a => prog(f(a.asInstanceOf[A]))))
  override def map[A, B](p: Prog[A], f: Fn[A, B]): Prog[B] = wrap(prog(p).map[Any](a => f(a.asInstanceOf[A])))
  def delay[A](p: Thunk[Prog[A]]): Prog[A] = wrap(Eff.delay[Any, Any](() => prog(p.get())))

  // the handler found last, by effect: a loop's operations find theirs once
  private var lastEffect: Effect = null
  private var last: OnJava.InForce = null

  def perform[X](op: Op[X]): Prog[X] =
    val e = op.effect
    val f = if e eq lastEffect then last else
      val found = inForce.find(_.effect eq e).getOrElse(throw IllegalStateException(s"an operation of $e with no handler of it in force"))
      lastEffect = e
      last = found
      found
    f.handler match
      case a: Answering[?, ?] => wrap(Eff.sync[Any, X](() => a.value(op)))
      case h => wrap(f.prompt.shift0[X](k => f.outer.clause(h, op, k)))

  /** the clause of `h` for `op`, in this monad — the handler's place — `k` the rest under the handler again */
  private def clause[X](h: Handler[?, ?], op: Op[X], k: java.util.function.Function[X, E]): E =
    prog(h.asInstanceOf[Handler[Any, Any]].apply[X](op, x => wrap(k.apply(x)), this))

  def handle[A, R](h: Handler[A, R], body: Program[A]): Prog[R] =
    val outer = this
    wrap(Eff.handle[Any, Any, Any](
      new Eff.Body[Any, Any, Any]:
        def in[M](p: Prompt[Any, M, Any]): Eff[M, Any] =
          val inside = OnJava(OnJava.InForce(h.effect, h, p.asInstanceOf[Prompt[Any, Any, Any]], outer) :: inForce)
          prog(body.in(inside)).asInstanceOf[Eff[M, Any]]
      ,
      a => prog(h.ret(a.asInstanceOf[A], outer))))

  def run[A](p: Prog[A]): A = Eff.run[A](prog(p).asInstanceOf[Eff[Eff.Top, A]])

object OnJava:
  /** a handler in force: its effect, its clauses, its prompt, the monad of its place */
  private final case class InForce(effect: Effect, handler: Handler[?, ?], prompt: Prompt[Any, Any, Any], outer: OnJava)

  /** okay-java's machine, the kernel's monad */
  val monad: Monad = OnJava(Nil)
