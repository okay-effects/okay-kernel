package okay.kernel.okaycore

import okay.core.{Effects, In, Top as CoreTop}
import okay.kernel.{Answering, Effect, Fn, Handler, Monad, Op, Prog, Program, Thunk}

/** the kernel's operations, every one, as one effect of okay-core's: its handler the kernel handler's */
final case class Dyn[X](op: Op[X])

/**
 * A MONAD OF THE KERNEL ON OKAY-CORE: its programs okay-core's, `c.Prog`, run by its machines — `zero`'s (Cont0, the
 * hybrid) or `cont`'s. The kernel's handlers are okay-core's of the effect `Dyn`; the context remembers the kernel's
 * effects of its row, innermost first, so an operation finds its path, `In`, to the nearest handler of its effect.
 * A handler's answer is the program outside it, flattened when the handler is left: so `k` is the rest with the
 * handler and its `ret`, deep, as the kernel says.
 */
final class OnCore private (val c: Effects, effs: List[Effect], top: CoreTop) extends Monad:
  private type P = c.M[Any, Any, Any]
  /** okay-core's program, as the kernel's: a wrapper — one object a node, the adapter's cost */
  private final class W[A](val p: Any) extends Prog[A]
  private inline def prog(x: Prog[?]): P = x.asInstanceOf[W[?]].p.asInstanceOf[P]
  private inline def wrap[A](p: Any): Prog[A] = W[A](p)

  def pure[A](a: A): Prog[A] = wrap(c.pure[Any, Any](a))
  def flatMap[A, B](p: Prog[A], f: Fn[A, Prog[B]]): Prog[B] =
    wrap(c.flatMap(prog(p))[Any, Any](a => prog(f(a.asInstanceOf[A]))))
  override def map[A, B](p: Prog[A], f: Fn[A, B]): Prog[B] = wrap(c.map(prog(p))(a => f(a.asInstanceOf[A])))
  def delay[A](p: Thunk[Prog[A]]): Prog[A] = wrap(c.delay[Any, Any, Any](prog(p.get())))

  // the paths found in this context, by effect: a loop's operations find theirs once
  private var lastEffect: Effect = null
  private var lastPath: In[?, ?] = null

  def perform[X](op: Op[X]): Prog[X] =
    val e = op.effect
    val in = if e eq lastEffect then lastPath else
      val i = effs.indexWhere(_ eq e)
      if i < 0 then throw IllegalStateException(s"an operation of $e with no handler of it in force")
      var path: In[?, ?] = In.Here()
      for _ <- 0 until i do path = In.There(path.asInstanceOf[In[Dyn, okay.core.Row]])
      lastEffect = e
      lastPath = path
      path
    wrap(c.perform[Dyn, Any, Any](Dyn(op.asInstanceOf[Op[Any]]), in.asInstanceOf[In[Dyn, c.Effs]]))

  def handle[A, R](h: Handler[A, R], body: Program[A]): Prog[R] =
    val outer = this
    val ch: okay.core.Handler[c.Prog, Dyn, Any, Any] = h match
      case a: Answering[A, R] @unchecked => new okay.core.Answering[c.Prog, Dyn, Any, Any]:
        def ret(x: Any): Any = a.ret(x.asInstanceOf[A], outer)
        def value[X](op: Dyn[X]): X = a.value(op.op)
      case _ => new okay.core.Handler[c.Prog, Dyn, Any, Any]:
        def ret(x: Any): Any = h.ret(x.asInstanceOf[A], outer)
        def apply[X](op: Dyn[X], k: X => c.Prog[Any]): c.Prog[Any] =
          c.pure(h.apply[X](op.op, x => flatten[R](k(x)), outer))
    wrap(prog(flatten[R](c.handle[Dyn, Any, Any](ch)((ctx: c.Handled[Dyn, Any]) ?=>
      prog(body.in(OnCore(ctx, h.effect :: effs, top))).asInstanceOf[c.Handling[Dyn, Any, Any, Any]]))))

  /** an answer that is a program, its own (wrapped): run on */
  private def flatten[R](p: Any): Prog[R] = wrap(c.flatMap(p.asInstanceOf[P])[Any, Any](inner => prog(inner.asInstanceOf[Prog[?]])))

  def run[A](p: Prog[A]): A = top.run[Any](prog(p).asInstanceOf[top.M[Any, Any, Any, Any]]).asInstanceOf[A]

object OnCore:
  /** okay-core's Cont0, its hybrid machine */
  val zero: Monad = OnCore(okay.zero.top.at[Any], Nil, okay.zero.top)
  /** okay-core's Cont, its loop */
  val cont: Monad = OnCore(okay.cont.top.at[Any], Nil, okay.cont.top)
