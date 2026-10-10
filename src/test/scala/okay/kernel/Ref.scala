package okay.kernel

/**
 * A MONAD, THE REFERENCE: the textbook — a program evaluates to its value or to an operation with the rest; a
 * handler takes those of its effect, its clause in its place, `k` the rest under it again, and passes the others on.
 * Recursive, not in constant stack: what every monad must agree with, on programs small enough
 */
object Ref extends Monad:
  private sealed trait N extends Prog[Any]
  private final case class Pure(a: Any) extends N
  private final case class FlatMap(m: N, f: Fn[Any, Any]) extends N
  private final case class Delay(p: Thunk[Any]) extends N
  private final case class Perform(op: Op[?]) extends N
  private final case class Handle(h: Handler[Any, Any], body: Program[?]) extends N
  private final case class Resumed(r: () => Res) extends N

  private enum Res:
    case Done(v: Any)
    case Cap(op: Op[?], k: Any => Res)
  import Res.*

  private def typed[A](n: N): Prog[A] = n.asInstanceOf[Prog[A]]
  def pure[A](a: A): Prog[A] = typed(Pure(a))
  def flatMap[A, B](p: Prog[A], f: Fn[A, Prog[B]]): Prog[B] = typed(FlatMap(p.asInstanceOf[N], f.asInstanceOf[Fn[Any, Any]]))
  def delay[A](p: Thunk[Prog[A]]): Prog[A] = typed(Delay(p.asInstanceOf[Thunk[Any]]))
  def perform[X](op: Op[X]): Prog[X] = typed(Perform(op))
  def handle[A, R](h: Handler[A, R], body: Program[A]): Prog[R] = typed(Handle(h.asInstanceOf[Handler[Any, Any]], body))
  def run[A](p: Prog[A]): A = eval(p.asInstanceOf[N]) match
    case Done(v) => v.asInstanceOf[A]
    case Cap(op, _) => throw IllegalStateException(s"an operation of ${op.effect} with no handler of it in force")

  private def andThen(r: Res, g: Any => Res): Res = r match
    case Done(v) => g(v)
    case Cap(op, k) => Cap(op, x => andThen(k(x), g))

  private def delimit(h: Handler[Any, Any], r: Res): Res = r match
    case Done(v) => eval(h.ret(v, Ref).asInstanceOf[N])
    case Cap(op, k) if op.effect eq h.effect =>
      val kk: Fn[Any, Prog[Any]] = x => typed(Resumed(() => delimit(h, k(x))))
      eval(h.apply[Any](op.asInstanceOf[Op[Any]], kk, Ref).asInstanceOf[N])
    case Cap(op, k) => Cap(op, x => delimit(h, k(x)))

  private def eval(n: N): Res = n match
    case Pure(a) => Done(a)
    case FlatMap(m, f) => andThen(eval(m), x => eval(f(x).asInstanceOf[N]))
    case Delay(p) => eval(p.get().asInstanceOf[N])
    case Perform(op) => Cap(op, x => Done(x))
    case Handle(h, body) => delimit(h, eval(body.in(Ref).asInstanceOf[N]))
    case Resumed(r) => r()
