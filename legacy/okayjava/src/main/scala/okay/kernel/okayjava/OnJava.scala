package okay.kernel.okayjava

import okay.core.{Eff, Prompt}
import okay.kernel.{Answering, Direct, Effect, Fn, Handler, Monad, Op, Prog, Program, Returning, Thunk}

/**
 * A MONAD OF THE KERNEL ON OKAY-JAVA: its programs okay-java's `Eff`, run by its machine — the prompts' one. A kernel
 * handler is an `Eff.handle`, its body given a fresh prompt; the context remembers the handlers in force, innermost
 * first, each with its prompt, so an operation goes to the nearest handler of its effect by `shift0` to that prompt —
 * its clause run outside, in the handler's place, `k` the rest with the handler (deep), as the kernel says. An
 * answering handler's operation is no capture: `Eff.sync`, its value read in place. okay-java's levels are types
 * only: here every program is at one level, `Any`.
 */
final class OnJava private (inForce: List[OnJava.InForce]) extends Direct:
  private type E = Eff[Any, Any]
  /** okay-java's program, as the kernel's: a wrapper — one object a node, the adapter's cost */
  private final class W[A](val p: E) extends Prog[A]
  private inline def raw(x: Prog[?]): E = x.asInstanceOf[W[?]].p
  private inline def wrap[A](p: Eff[?, ?]): Prog[A] = W[A](p.asInstanceOf[E])

  def pure[A](a: A): Prog[A] = wrap(Eff.pure[Any, A](a))
  def flatMap[A, B](p: Prog[A], f: Fn[A, Prog[B]]): Prog[B] = wrap(raw(p).flatMap[Any](a => raw(f(a.asInstanceOf[A]))))
  override def map[A, B](p: Prog[A], f: Fn[A, B]): Prog[B] = wrap(raw(p).map[Any](a => f(a.asInstanceOf[A])))
  def delay[A](p: Thunk[Prog[A]]): Prog[A] = wrap(Eff.delay[Any, Any](() => raw(p.get())))

  def perform[X](op: Op[X]): Prog[X] = wrap(performed(op))

  // SCALA'S TYPED PROGRAMS, BUILT DIRECTLY: okay-java's programs themselves, a bind's function the program's own
  def directPure(a: Any): Any = Eff.pure[Any, Any](a)
  def directFlatMap(p: Any, f: Any => Any): Any =
    // okay-java binds a java.util.function.Function: the function adapted, one closure
    p.asInstanceOf[E].flatMap[Any](a => f(a).asInstanceOf[E])
  def directMap(p: Any, f: Any => Any): Any = p.asInstanceOf[E].map[Any](a => f(a))
  def directDelay(p: () => Any): Any = Eff.delay[Any, Any](() => p().asInstanceOf[E])
  def directPerform(op: Op[?]): Any = performed(op)
  def directHandle(h: Handler[?, ?], body: Program[?]): Any = raw(handle(h.asInstanceOf[Handler[Any, Any]], body.asInstanceOf[Program[Any]]))
  def prog[A](p: Any): Prog[A] = wrap(p.asInstanceOf[E])
  def unprog(p: Prog[?]): Any = raw(p)

  /** an operation, okay-java's: to the nearest handler of its effect */
  private def performed[X](op: Op[X]): E =
    val e = op.effect
    // the nearest handler of the effect, by identity, innermost first
    var rest = inForce
    while rest.nonEmpty && (rest.head.effect ne e) do rest = rest.tail
    if rest.isEmpty then throw IllegalStateException(s"an operation of $e with no handler of it in force")
    val f = rest.head
    f.handler match
      case a: Answering[?, ?] => Eff.sync[Any, X](() => a.value(op)).asInstanceOf[E]
      case h => f.prompt.shift0[X](k => f.outer.clause(h, op, k)).asInstanceOf[E]

  /** the clause of `h` for `op`, in this monad — the handler's place — `k` the rest under the handler again */
  private def clause[X](h: Handler[?, ?], op: Op[X], k: java.util.function.Function[X, E]): E =
    raw(h.asInstanceOf[Handler[Any, Any]].apply[X](op, x => wrap(k.apply(x)), this))

  def handle[A, R](h: Handler[A, R], body: Program[A]): Prog[R] =
    val outer = this
    wrap(Eff.handle[Any, Any, Any](
      new Eff.Body[Any, Any, Any]:
        def in[M](p: Prompt[Any, M, Any]): Eff[M, Any] =
          val inside = OnJava(OnJava.InForce(h.effect, h, p.asInstanceOf[Prompt[Any, Any, Any]], outer) :: inForce)
          raw(body.in(inside)).asInstanceOf[Eff[M, Any]]
      ,
      // a return that is a value: no program built in the kernel's terms and taken apart
      h match
        case r: Returning[A, R] @unchecked => a => Eff.pure[Any, Any](r.answer(a.asInstanceOf[A]))
        case _ => a => raw(h.ret(a.asInstanceOf[A], outer))))

  def run[A](p: Prog[A]): A = Eff.run[A](raw(p).asInstanceOf[Eff[Eff.Top, A]])

object OnJava:
  /** a handler in force: its effect, its clauses, its prompt, the monad of its place */
  private final case class InForce(effect: Effect, handler: Handler[?, ?], prompt: Prompt[Any, Any, Any], outer: OnJava)

  /** okay-java's machine, the kernel's monad */
  val monad: Monad = OnJava(Nil)
