package okay.kernel.okaycore

import okay.core.{Effects, In, Top as CoreTop}
import okay.kernel.{Answering, Direct, Effect, Fn, Handler, Monad, Op, Prog, Program, Returning, Thunk}

/** the kernel's operations, every one, as one effect of okay-core's — the kernel's `Op` itself, no wrapper: its
 * handler the kernel handler's */
type Dyn[X] = Op[X]

/**
 * A MONAD OF THE KERNEL ON OKAY-CORE: its programs okay-core's, `c.Prog`, run by its machines — `zero`'s (Cont, the
 * hybrid) or `cont`'s. The kernel's handlers are okay-core's of the effect `Dyn`; the context remembers the kernel's
 * effects of its row, innermost first, so an operation finds its path, `In`, to the nearest handler of its effect.
 * A handler's answer is the program outside it, flattened when the handler is left: so `k` is the rest with the
 * handler and its `ret`, deep, as the kernel says.
 */
final class OnCore private (val c: Effects, effs: List[Effect], top: CoreTop) extends Direct:
  private type P = c.M[Any, Any, Any]
  /** okay-core's program, as the kernel's: a wrapper — one object a node, the adapter's cost */
  private final class W[A](val p: Any) extends Prog[A]
  private inline def raw(x: Prog[?]): P = x.asInstanceOf[W[?]].p.asInstanceOf[P]
  private inline def wrap[A](p: Any): Prog[A] = W[A](p)

  def pure[A](a: A): Prog[A] = wrap(c.pure[Any, Any](a))
  def flatMap[A, B](p: Prog[A], f: Fn[A, Prog[B]]): Prog[B] =
    wrap(c.flatMap(raw(p))[Any, Any](a => raw(f(a.asInstanceOf[A]))))
  override def map[A, B](p: Prog[A], f: Fn[A, B]): Prog[B] = wrap(c.map(raw(p))(a => f(a.asInstanceOf[A])))
  def delay[A](p: Thunk[Prog[A]]): Prog[A] = wrap(c.delay[Any, Any, Any](raw(p.get())))

  def perform[X](op: Op[X]): Prog[X] = wrap(performed(op))

  // SCALA'S TYPED PROGRAMS, BUILT DIRECTLY: okay-core's programs themselves, a bind's function the program's own
  def directPure(a: Any): Any = c.pure[Any, Any](a)
  def directFlatMap(p: Any, f: Any => Any): Any = c.flatMap(p.asInstanceOf[P])[Any, Any](f.asInstanceOf[Any => P])
  def directMap(p: Any, f: Any => Any): Any = c.map(p.asInstanceOf[P])(f)
  def directDelay(p: () => Any): Any = c.delay[Any, Any, Any](p().asInstanceOf[P])
  def directPerform(op: Op[?]): Any = performed(op)
  def directHandle(h: Handler[?, ?], body: Program[?]): Any = raw(handle(h.asInstanceOf[Handler[Any, Any]], body.asInstanceOf[Program[Any]]))
  def prog[A](p: Any): Prog[A] = wrap(p)
  def unprog(p: Prog[?]): Any = raw(p)

  /** an operation, okay-core's: to the nearest handler of its effect */
  private def performed[X](op: Op[X]): P =
    val e = op.effect
    // the nearest handler of the effect, by identity: its index among those in force, innermost first
    var i = 0
    var rest = effs
    while rest.nonEmpty && (rest.head ne e) do
      rest = rest.tail
      i += 1
    if rest.isEmpty then throw IllegalStateException(s"an operation of $e with no handler of it in force")
    c.perform[Dyn, Any, X](op, OnCore.path(i).asInstanceOf[In[Dyn, c.Effs]]).asInstanceOf[P]

  def handle[A, R](h: Handler[A, R], body: Program[A]): Prog[R] = h match
    case r: Returning[A, R] @unchecked => handleReturning(h, r, body)
    case _ => handleGeneral(h, body)

  /** a handler whose return is a value: okay-core's own — its answer `R` itself, a clause's program okay-core's,
   * `k`'s too: nothing flattened */
  private def handleReturning[A, R](h: Handler[A, R], r: Returning[A, R], body: Program[A]): Prog[R] =
    val outer = this
    val ch: okay.core.Handler[c.Prog, Dyn, Any, Any] = h match
      case a: Answering[A, R] @unchecked => new okay.core.Answering[c.Prog, Dyn, Any, Any]:
        def ret(x: Any): Any = r.answer(x.asInstanceOf[A])
        def value[X](op: Dyn[X]): X = a.value(op)
      case _ => new okay.core.Handler[c.Prog, Dyn, Any, Any]:
        def ret(x: Any): Any = r.answer(x.asInstanceOf[A])
        def apply[X](op: Dyn[X], k: X => c.Prog[Any]): c.Prog[Any] =
          raw(h.apply[X](op, x => wrap[R](k(x)), outer)).asInstanceOf[c.Prog[Any]]
    wrap(c.handle[Dyn, Any, Any](ch)((ctx: c.Handled[Dyn, Any]) ?=>
      raw(body.in(OnCore(ctx, h.effect :: effs, top))).asInstanceOf[c.Handling[Dyn, Any, Any, Any]]))

  private def handleGeneral[A, R](h: Handler[A, R], body: Program[A]): Prog[R] =
    val outer = this
    val ch: okay.core.Handler[c.Prog, Dyn, Any, Any] = h match
      case a: Answering[A, R] @unchecked => new okay.core.Answering[c.Prog, Dyn, Any, Any]:
        def ret(x: Any): Any = a.ret(x.asInstanceOf[A], outer)
        def value[X](op: Dyn[X]): X = a.value(op)
      case _ => new okay.core.Handler[c.Prog, Dyn, Any, Any]:
        def ret(x: Any): Any = h.ret(x.asInstanceOf[A], outer)
        def apply[X](op: Dyn[X], k: X => c.Prog[Any]): c.Prog[Any] =
          c.pure(h.apply[X](op, x => flatten[R](k(x)), outer))
    wrap(raw(flatten[R](c.handle[Dyn, Any, Any](ch)((ctx: c.Handled[Dyn, Any]) ?=>
      raw(body.in(OnCore(ctx, h.effect :: effs, top))).asInstanceOf[c.Handling[Dyn, Any, Any, Any]]))))

  /** an answer that is a program, its own (wrapped): run on */
  private def flatten[R](p: Any): Prog[R] = wrap(c.flatMap(p.asInstanceOf[P])[Any, Any](inner => raw(inner.asInstanceOf[Prog[?]])))

  def run[A](p: Prog[A]): A = top.run[Any](raw(p).asInstanceOf[top.M[Any, Any, Any, Any]]).asInstanceOf[A]

object OnCore:
  /** the path past `i` effects — the same in every context, made once: `There` `i` times, then `Here` */
  @volatile private var paths: Array[In[?, ?]] = Array(In.Here())
  private def path(i: Int): In[?, ?] =
    val known = paths
    if i < known.length then known(i)
    else
      val grown = new Array[In[?, ?]](math.max(i + 1, known.length * 2))
      System.arraycopy(known, 0, grown, 0, known.length)
      var j = known.length
      while j < grown.length do
        grown(j) = In.There(grown(j - 1).asInstanceOf[In[Dyn, okay.core.Row]])
        j += 1
      paths = grown
      grown(i)

  /** okay-core's Cont, its hybrid machine */
  val zero: Monad = OnCore(okay.zero.top.at[Any], Nil, okay.zero.top)
  /** okay-core's Cont, its loop */
  val cont: Monad = OnCore(okay.cont.top.at[Any], Nil, okay.cont.top)
