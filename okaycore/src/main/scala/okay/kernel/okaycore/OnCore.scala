package okay.kernel.okaycore

import okay.core.{Effects, In, Top as CoreTop}
import okay.kernel.{Answering, Effect, Fn, Handler, Monad, Op, Program, Thunk}

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
  private inline def prog(x: Any): P = x.asInstanceOf[P]

  def pure(a: Any): Any = c.pure[Any, Any](a)
  def flatMap(p: Any, f: Fn[Any, Any]): Any = c.flatMap(prog(p))[Any, Any](a => prog(f(a)))
  override def map(p: Any, f: Fn[Any, Any]): Any = c.map(prog(p))(a => f(a))
  def delay(p: Thunk[Any]): Any = c.delay[Any, Any, Any](prog(p.get()))

  // the paths found in this context, by effect: a loop's operations find theirs once
  private var lastEffect: Effect = null
  private var lastPath: In[?, ?] = null

  def perform(op: Op[?]): Any =
    val e = op.effect
    val in = if e eq lastEffect then lastPath else
      val i = effs.indexWhere(_ eq e)
      if i < 0 then throw IllegalStateException(s"an operation of $e with no handler of it in force")
      var path: In[?, ?] = In.Here()
      for _ <- 0 until i do path = In.There(path.asInstanceOf[In[Dyn, okay.core.Row]])
      lastEffect = e
      lastPath = path
      path
    c.perform[Dyn, Any, Any](Dyn(op.asInstanceOf[Op[Any]]), in.asInstanceOf[In[Dyn, c.Effs]])

  def handle(h: Handler, body: Program[?]): Any =
    val outer = this
    val ch: okay.core.Handler[c.Prog, Dyn, Any, Any] = h match
      case a: Answering => new okay.core.Answering[c.Prog, Dyn, Any, Any]:
        def ret(x: Any): Any = a.ret(x, outer)
        def value[X](op: Dyn[X]): X = a.value(op.op).asInstanceOf[X]
      case _ => new okay.core.Handler[c.Prog, Dyn, Any, Any]:
        def ret(x: Any): Any = h.ret(x, outer)
        def apply[X](op: Dyn[X], k: X => c.Prog[Any]): c.Prog[Any] =
          c.pure(h.apply(op.op, x => flatten(k(x.asInstanceOf[X])), outer))
    flatten(c.handle[Dyn, Any, Any](ch)((ctx: c.Handled[Dyn, Any]) ?=>
      body.in(OnCore(ctx, h.effect :: effs, top)).asInstanceOf[c.Handling[Dyn, Any, Any, Any]]))

  /** an answer that is a program: run on */
  private def flatten(p: Any): Any = c.flatMap(prog(p))[Any, Any](inner => prog(inner))

  def run(p: Any): Any = top.run[Any](p.asInstanceOf[top.M[Any, Any, Any, Any]])

object OnCore:
  /** okay-core's Cont0, its hybrid machine */
  val zero: Monad = OnCore(okay.zero.top.at[Any], Nil, okay.zero.top)
  /** okay-core's Cont, its loop */
  val cont: Monad = OnCore(okay.cont.top.at[Any], Nil, okay.cont.top)
