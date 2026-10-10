package okay.kernel

/**
 * A MONAD, THE REFERENCE: the textbook — a program evaluates to its value or to an operation with the rest; a
 * handler takes those of its effect, its clause in its place, `k` the rest under it again, and passes the others on.
 * Recursive, not in constant stack: what every monad must agree with, on programs small enough
 */
object Ref extends Monad:
  private sealed trait N
  private final case class Pure(a: Any) extends N
  private final case class FlatMap(m: N, f: Fn[Any, Any]) extends N
  private final case class Delay(p: Thunk[Any]) extends N
  private final case class Perform(op: Op[?]) extends N
  private final case class Handle(h: Handler, body: Program[?]) extends N
  private final case class Resumed(r: () => Res) extends N

  private enum Res:
    case Done(v: Any)
    case Cap(op: Op[?], k: Any => Res)
  import Res.*

  def pure(a: Any): Any = Pure(a)
  def flatMap(p: Any, f: Fn[Any, Any]): Any = FlatMap(p.asInstanceOf[N], f)
  def delay(p: Thunk[Any]): Any = Delay(p)
  def perform(op: Op[?]): Any = Perform(op)
  def handle(h: Handler, body: Program[?]): Any = Handle(h, body)
  def run(p: Any): Any = eval(p.asInstanceOf[N]) match
    case Done(v) => v
    case Cap(op, _) => throw IllegalStateException(s"an operation of ${op.effect} with no handler of it in force")

  private def andThen(r: Res, g: Any => Res): Res = r match
    case Done(v) => g(v)
    case Cap(op, k) => Cap(op, x => andThen(k(x), g))

  private def delimit(h: Handler, r: Res): Res = r match
    case Done(v) => eval(h.ret(v, Ref).asInstanceOf[N])
    case Cap(op, k) if op.effect eq h.effect =>
      val kk: Fn[Any, Any] = x => Resumed(() => delimit(h, k(x)))
      eval(h.apply(op, kk, Ref).asInstanceOf[N])
    case Cap(op, k) => Cap(op, x => delimit(h, k(x)))

  private def eval(n: N): Res = n match
    case Pure(a) => Done(a)
    case FlatMap(m, f) => andThen(eval(m), x => eval(f(x).asInstanceOf[N]))
    case Delay(p) => eval(p.get().asInstanceOf[N])
    case Perform(op) => Cap(op, x => Done(x))
    case Handle(h, body) => delimit(h, eval(body.in(Ref).asInstanceOf[N]))
    case Resumed(r) => r()
