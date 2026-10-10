package okay.kernel

/**
 * A MONAD: okay-java's machine of steps — programs as nodes, run over an explicit stack of handlers on the heap, so
 * handlers nest as deep as the heap allows, on every platform. One step a kind of node, a method of its own (the
 * loop's call to it a virtual one): each compiled apart, small. A handler is found by its effect, walked to; a
 * capture crosses the handlers between with no allocation; resumed where it was captured, a continuation keeps the
 * stack as it was; a clause's `k(x)` bound puts its function in the handler, no frame of its own.
 */
object Steps extends Monad:

  // THE NODES: this monad's programs
  private sealed abstract class Node extends Prog[Any]:
    def step(r: Run): Node
  private final class Return(val value: Any) extends Node:
    def step(r: Run): Node = r.value(value)
  private final class Bind(val m: Node, val f: Fn[Any, Any]) extends Node:
    def step(r: Run): Node = r.bind(this)
  private final class MapN(val m: Node, val f: Fn[Any, Any]) extends Node:
    def step(r: Run): Node = r.map(this)
  private final class Delay(val c: Thunk[Any]) extends Node:
    def step(r: Run): Node = c.get().asInstanceOf[Node]
  private final class Perform(val op: Op[?]) extends Node:
    def step(r: Run): Node = r.perform(op, null)
  /** an operation bound: one node */
  private final class Suspend(val op: Op[?], val k: Fn[Any, Any]) extends Node:
    def step(r: Run): Node = r.perform(op, k)
  private final class Handle(val h: Handler[Any, Any], val body: Program[?]) extends Node:
    def step(r: Run): Node = r.handle(this, null)
  /** a captured rest resumed with `x`: `k` frames or a function, `upTo` its handler if none crossed, else the Kont */
  private final class Resume(val x: Any, val k: AnyRef, val upTo: AnyRef) extends Node:
    def step(r: Run): Node = r.resume(this, null)

  private inline def node(p: Any): Node = p.asInstanceOf[Node]
  private inline def typed[A](n: Node): Prog[A] = n.asInstanceOf[Prog[A]]

  def pure[A](a: A): Prog[A] = typed(Return(a))
  def flatMap[A, B](p: Prog[A], f: Fn[A, Prog[B]]): Prog[B] = typed(p match
    case o: Perform => Suspend(o.op, f.asInstanceOf[Fn[Any, Any]])
    case n => Bind(node(n), f.asInstanceOf[Fn[Any, Any]]))
  override def map[A, B](p: Prog[A], f: Fn[A, B]): Prog[B] = typed(MapN(node(p), f.asInstanceOf[Fn[Any, Any]]))
  def delay[A](p: Thunk[Prog[A]]): Prog[A] = typed(Delay(p.asInstanceOf[Thunk[Any]]))
  def perform[X](op: Op[X]): Prog[X] = typed(Perform(op))
  def handle[A, R](h: Handler[A, R], body: Program[A]): Prog[R] = typed(Handle(h.asInstanceOf[Handler[Any, Any]], body))

  private val Done: Node = Return(null)

  def run[A](p: Prog[A]): A =
    val r = Run()
    var c = node(p)
    while c ne Done do c = c.step(r)
    r.result.asInstanceOf[A]

  // THE STACK
  private sealed abstract class Frames
  private object End extends Frames
  private final class Then(val f: Fn[Any, Any], val next: Frames) extends Frames
  private final class Mapped(val f: Fn[Any, Any], val next: Frames) extends Frames

  private sealed abstract class Meta
  private object TopMeta extends Meta
  /** a handler in force: the function its answer is bound to if any, the frames outside it, the handlers further out */
  private final class Seg(val h: Handler[Any, Any], val bound: Fn[Any, Any] | Null, val outer: Frames, val next: Meta) extends Meta
  /** the first `n` handlers of `m`, then `tail`: a resumed continuation's, shared, not copied */
  private final class Take(val m: Meta, val n: Int, val tail: Meta) extends Meta

  private def take(m0: Meta, n: Int, tail: Meta): Meta =
    if n == 0 then tail
    else
      var m = m0
      while m.isInstanceOf[Take] && n <= m.asInstanceOf[Take].n do m = m.asInstanceOf[Take].m
      Take(m, n, tail)

  private def head(m0: Meta): Seg =
    var m = m0
    while true do
      m match
        case s: Seg => return s
        case t: Take => m = if t.n > 0 then t.m else t.tail
        case _ => throw IllegalStateException("no handler")
    throw IllegalStateException()

  private def drop(m0: Meta, d0: Int): Meta =
    var m = m0
    var d = d0
    while d > 0 do
      m match
        case s: Seg => m = s.next; d -= 1
        case t: Take =>
          if d < t.n then return take(drop(t.m, d), t.n - d, t.tail)
          d -= t.n
          m = t.tail
        case _ => throw IllegalStateException("no handler")
    m

  /** the index of the nearest handler of `e` among the first `limit` of `m`: `-1 - n` if none */
  private def indexOf(m0: Meta, limit: Int, e: Effect): Int =
    var m = m0
    var seen = 0
    while seen < limit do
      m match
        case s: Seg =>
          if s.h.effect eq e then return seen
          seen += 1
          m = s.next
        case t: Take =>
          val n = math.min(t.n, limit - seen)
          val i = indexOf(t.m, n, e)
          if i >= 0 then return seen + i
          seen += n
          m = t.tail
        case _ => return -1 - seen
    -1 - seen

  private def index(ms: Meta, e: Effect): Int =
    val i = indexOf(ms, Int.MaxValue, e)
    if i < 0 then throw IllegalStateException(s"an operation of $e with no handler of it in force")
    i

  /** a captured continuation: the rest (frames or a function), the handlers crossed and the one captured up to */
  private final class Kont(val k: AnyRef, val ms: Meta, val n: Int) extends Fn[Any, Prog[Any]]:
    def apply(x: Any): Prog[Any] = Resume(x, k, if n == 0 && ms.isInstanceOf[Seg] then ms else this)
    def up: Seg = if n == 0 && ms.isInstanceOf[Seg] then ms.asInstanceOf[Seg] else head(drop(ms, n))

  private final class Run:
    var k: Frames = End
    var ms: Meta = TopMeta
    var result: Any = null

    def bind(b: Bind): Node = b.m match
      case r: Return => node(b.f(r.value))
      // a handler or a resumption bound: the function to its handler, no frame made
      case r: Resume => resume(r, b.f)
      case h: Handle => handle(h, b.f)
      case d: Delay => k = Then(b.f, k); node(d.c.get())
      case m => k = Then(b.f, k); m

    def map(m: MapN): Node =
      k = Mapped(m.f, k)
      m.m match
        case d: Delay => node(d.c.get())
        case n => n

    def handle(h: Handle, bound: Fn[Any, Any] | Null): Node =
      ms = Seg(h.h, bound, k, ms)
      k = End
      node(h.body.in(Steps))

    /** an operation: to the nearest handler of its effect — answered in place if it answers so, else its clause in
     * the handler's place, `k` the rest, `then` the function bound to the operation's value if any */
    def perform(op: Op[?], bound: Fn[Any, Any] | Null): Node =
      val e = op.effect
      var d: Seg = null
      var i = 0
      ms match
        case near: Seg if near.h.effect eq e => d = near
        case _ => i = index(ms, e); d = head(drop(ms, i))
      d.h match
        case a: Answering[?, ?] =>
          val v = a.asInstanceOf[Answering[Any, Any]].value[Any](op.asInstanceOf[Op[Any]])
          if bound == null then value(v) else node(bound(v))
        case h =>
          val kk: AnyRef = if bound == null then k else if k eq End then bound else Then(bound, k)
          val kont = Kont(kk, ms, i)
          var c = node(h.apply[Any](op.asInstanceOf[Op[Any]], kont, Steps))
          ms = if i == 0 && (ms eq d) then d.next else drop(ms, i + 1)
          k = d.outer
          val b = d.bound
          if b != null then
            c match
              case a: Return => c = node(b(a.value))
              case _ => k = Then(b, k)
          // the usual clauses, k(x), bound or mapped: resumed right here, no step of the loop between
          c match
            case r: Resume => resume(r, null)
            case bb: Bind if bb.m.isInstanceOf[Resume] => resume(bb.m.asInstanceOf[Resume], bb.f)
            case mp: MapN if mp.m.isInstanceOf[Resume] => k = Mapped(mp.f, k); resume(mp.m.asInstanceOf[Resume], null)
            case _ => c

    /** a continuation resumed here: x through its rest, then its handlers again, the frames here outside them */
    def resume(r: Resume, bound: Fn[Any, Any] | Null): Node =
      var x = r.x
      r.k match
        case fr0: Frames =>
          var fr = fr0
          var next: Node = null
          fr match
            case m: Mapped =>
              x = m.f(x)
              fr = m.next
              while fr.isInstanceOf[Mapped] do
                val mm = fr.asInstanceOf[Mapped]
                x = mm.f(x)
                fr = mm.next
              fr match
                case t: Then => next = node(t.f(x)); fr = t.next
                case _ =>
            case t: Then =>
              next = node(t.f(x))
              fr = t.next
            case _ =>
          if next != null then
            ms = again(r, bound, k, ms)
            k = fr
            next
          else r.upTo match
            case up: Seg =>
              // its rest done, its handler next: the frames and handlers here as they are, its return applied
              val c = node(up.h.ret(x, Steps))
              if bound == null then c
              else c match
                case a: Return => node(bound(a.value))
                case _ => k = Then(bound, k); c
            case _ =>
              ms = again(r, bound, k, ms)
              k = End
              value(x)
        case f =>
          ms = again(r, bound, k, ms)
          k = End
          node(f.asInstanceOf[Fn[Any, Any]](x))

    /** a value: through the maps to the first bind, or out of the handler — its return, outside it */
    def value(v0: Any): Node =
      var v = v0
      while true do
        while k.isInstanceOf[Mapped] do
          val m = k.asInstanceOf[Mapped]
          v = m.f(v)
          k = m.next
        var c: Node = null
        k match
          case t: Then =>
            k = t.next
            c = node(t.f(v))
          case _ =>
            val seg = ms match
              case plain: Seg => ms = plain.next; plain
              case TopMeta => result = v; return Done
              case m => ms = drop(m, 1); head(m)
            k = seg.outer
            c = node(seg.h.ret(v, Steps))
            val b = seg.bound
            if b != null then
              c match
                case a: Return => c = node(b(a.value))
                case _ => k = Then(b, k)
        c match
          case r: Return => v = r.value
          case _ => return c
      Done

  private def again(r: Resume, bound: Fn[Any, Any] | Null, k: Frames, ms: Meta): Meta = r.upTo match
    case up: Seg =>
      if bound == null && up.bound == null && (k eq up.outer) && (ms eq up.next) then up
      else Seg(up.h, bound, k, ms)
    case kont =>
      val kn = kont.asInstanceOf[Kont]
      val at = drop(kn.ms, kn.n)
      val up = head(at)
      if bound == null && up.bound == null && (k eq up.outer) && (ms eq up.next) then
        if at eq up then kn.ms else take(kn.ms, kn.n, up)
      else take(kn.ms, kn.n, Seg(up.h, bound, k, ms))
