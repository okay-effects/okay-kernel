package okay.kernel.onokay

import okay.cont.{At, Cont, Machine}
import okay.kernel.{Aborting, Answering, Answers, Computation, Effects, Fn, General, Handler, Op, Prog, Program, Resume,
  Returning, Step, Thunk, Type}
import scala.util.control.NonFatal

/**
 * OKAY AS AN IMPLEMENTATION OF THE KERNEL: okay-kernel's `Effects` on okay's machine — okay-cont's `Cont`, the nodes
 * `okay.Effects.machine` compiles every program of okay to, run by `okay.cont.Machine`. A kernel program (`Prog`) IS
 * a program of okay's machine (`P`, a `Cont` in a box): `pure` a `Return`, `flatMap` a `Bind`, `delay` a `Delay`, a
 * handler a `Reset`, an operation a `Shift0` — nothing of the bridge's own runs a program, the machine's loop does,
 * in constant stack.
 *
 * HOW. okay finds a handler at COMPILE time, by the row (`Member`, `Reach`: so many delimiters out); the kernel finds
 * it at RUN time, by the operation's `Type`, where the operation runs — a `k` resumed under other handlers sees those.
 * So the bridge does not use okay's static reach for the kernel's programs: every operation is a REQUEST to the
 * nearest delimiter — `Shift0(k => Return(request))` — and every delimiter of the bridge is a `Reset` whose answer
 * goes to its DISPATCH, a frame outside it (`Delim`): the body's value (`Done`), or a request with `k`, the rest up
 * to it. A delimiter takes the requests it concerns and FORWARDS the rest outward — one `Shift0` again, its `k` the
 * composition: the rest under it, it put back (`again`), under the next (Kammar–Lindley–Oury's deep handlers by
 * shift0/reset, on okay's machine). The lookup is the reference machine's `indexWhere` done by the stack itself.
 *
 *  - Handlers, by shape: `Answering` resumes `k` with `value(op)`; `Aborting` resumes it with an ABORT (a signal,
 *    below) unwinding from where the operation was to the handler, its finalizers run on the way; `General` runs the
 *    clause in the handler's place with `k` deep (the handler put back); a return `Returning`'s `answer`, else
 *    `General`'s `ret`, else the value.
 *  - `shift0`: a request only a handler's delimiter or a `$`'s takes — the bridge's others (`recover`, `ensuring`,
 *    `uncancelable`) forward it, so they are inside its `k`, as the reference's frames are. `$`: a delimiter whose
 *    return is `k`.
 *  - FAILURES, ABORTS, CANCELLATION: okay has none as primitives, so they are requests too — `Unwind`, `k` dropped —
 *    to the nearest delimiter taking them: `recover` (a failure), `ensuring` (any: the finalizer, then on), an
 *    `Aborting` handler (its own abort), a region (a cancellation pending there turns any into a cancellation), the
 *    top (the step). An exception of the program's functions — a bind's, a delay's, a clause, a `value`, a
 *    `Program.in` — is caught where the bridge calls it and unwinds from there. What an operation is answered can be
 *    a SIGNAL (a failure, an abort, a cancellation, a cancellation pending): the operation reads its value and unwinds
 *    from where it was performed — `k.fail`, `k.cancel`, an abort through finalizers.
 *  - EXPORT (`computation`): the program under the top delimiter (`Top`): a request no handler took is the host's in
 *    place if it answers its effect, else `Step.Yield` with `k` as the `Resume` — each call a run of okay's machine
 *    from that `k`: multi-shot, from any thread (okay's captured pieces are immutable). IMPORT (`foreign`): the
 *    handlers in force asked for (`Env`, a request every handler adds itself to), the computation started with them
 *    as its host in place, its yields performed here as operations of this program.
 *
 * okay's OWN programs (`Free[R, A]`, its row mapped to kernel types, `Mapping`) become kernel programs (`lift`,
 * `exported`) with their operations reaching the bridge by okay's own static reach — `Reach.Here` from a `Border`
 * around the program — and a kernel computation becomes okay's program (`imported`), its yields performed by okay's
 * handlers by their paths in the row.
 *
 * TRUSTED STEPS, two, each one method: `trust` (the reference machine's: a value given where the program, built typed,
 * expects it) and `level` (okay's stack indices are phantom types the machine never reads: the bridge's programs are
 * built at one index, `U`, and a `Reset`'s body and a `Shift0` are seen at it).
 */
object OnOkay extends Effects:

  override def toString: String = "OnOkay"

  /** okay's program, untyped: every program of the bridge at one stack index (`level`) */
  type U = Cont[EmptyTuple, EmptyTuple, Any]
  /** the index inside one delimiter: a `Reset`'s body's, a `Shift0`'s */
  private type Inside = Cont[At[EmptyTuple, Any] *: EmptyTuple, At[EmptyTuple, Any] *: EmptyTuple, Any]

  /** A KERNEL PROGRAM ON OKAY'S MACHINE: okay's program itself, in a box (`Cont` is okay's enum) */
  final class P[A] private[onokay] (val c: U) extends Prog[A]

  // -- the trusted steps

  /** THE TRUSTED STEP of the values: a value given where the program, built typed, expects it */
  private[onokay] def trust[T](v: Any): T = v.asInstanceOf[T]

  /** THE TRUSTED STEP of the index: okay's `Cont` is indexed by its stacks of answer types, phantom — erased, never
   * read by the machine, which walks its stack by its nodes. The bridge types its programs untyped at one index;
   * a delimiter's body and a capture are the same objects seen one level in */
  private[onokay] def level[T <: Cont[?, ?, ?]](c: Cont[?, ?, ?]): T = c.asInstanceOf[T]

  private[onokay] def unbox(p: Prog[?]): U = p match
    case p: P[?] => p.c
    case _ => throw IllegalArgumentException(s"not a program of okay's bridge: $p")

  // -- okay's nodes, untyped

  private[onokay] def pureU(a: Any): U = Cont.Return(a)
  private[onokay] def bind(m: U, f: Any => U): U = Cont.Bind[EmptyTuple, EmptyTuple, EmptyTuple, Any, Any](m, f)
  private[onokay] def delayU(t: => U): U = Cont.Delay(() => t)
  /** a request to the nearest delimiter: `k` the rest up to it, the delimiter included */
  private[onokay] def request(mk: (Any => U) => Req): U =
    level[U](Cont.Shift0[EmptyTuple, EmptyTuple, EmptyTuple, Any, Any, Any](k => Cont.Return(mk(k))))
  /** `body` under a delimiter of the bridge: its value, or a request, to `d` outside it */
  private[onokay] def delimit(body: U, d: Delim): U =
    bind(Cont.Reset[EmptyTuple, EmptyTuple, Any, Any](level[Inside](bind(body, v => pureU(Done(v))))), d)

  /** a program the user's code gives: its exception a failure where it is run */
  private[onokay] def guard(p: => Prog[?]): U =
    try unbox(p) catch case NonFatal(e) => unwind(FailSig(e))
  private def built(p: Program[?]): U = guard(p.in(this))

  // -- Effects

  def pure[A](a: A): Prog[A] = P(pureU(a))
  def flatMap[A, B](p: Prog[A], f: Fn[A, Prog[B]]): Prog[B] = P(bind(unbox(p), v => guard(f.apply(trust[A](v)))))
  override def map[A, B](p: Prog[A], f: Fn[A, B]): Prog[B] =
    P(bind(unbox(p), v => try pureU(f.apply(trust[A](v))) catch case NonFatal(e) => unwind(FailSig(e))))
  def delay[A](p: Thunk[Prog[A]]): Prog[A] = P(delayU(guard(p.get())))
  def perform[X](op: Op[X]): Prog[X] = P(performU(op))
  def handle[A, R](h: Handler[A, R], body: Program[A]): Prog[R] = P(delayU(delimit(built(body), HandlerD(h))))
  def shift0[A, I, O](body: Fn[Fn[A, Prog[I]], Prog[O]]): Prog[A] =
    P(bind(request(k => Shift(trust[Fn[Fn[Any, Prog[Any]], Prog[Any]]](body), k)), check))
  def dollar[A, I, O](body: Program[A], k: Fn[A, Prog[I]]): Prog[O] =
    P(delayU(delimit(built(body), DollarD(trust[Fn[Any, Prog[Any]]](k)))))
  def failed[A](e: Throwable): Prog[A] = P(delayU(unwind(FailSig(e))))
  def recover[A](body: Program[A], h: Fn[Throwable, Prog[A]]): Prog[A] = P(delayU(delimit(built(body), RecoverD(h))))
  def ensuring[A](body: Program[A], finalizer: Program[Void]): Prog[A] =
    P(delayU(delimit(built(body), EnsuringD(finalizer))))
  def uncancelable[A](body: Program[A]): Prog[A] = P(delayU(delimit(built(body), RegionD(pending = false))))
  def foreign[A](c: Computation[A]): Prog[A] =
    P(bind(request(k => Env(Nil, k)), env => drive(safely(c.start(answersFrom(trust[EnvV](env)))), null)))

  /** an operation: a request; its answer read — a signal unwinds from here */
  private[onokay] def performU(op: Op[?]): U = bind(request(k => Perform(op, k, region = false)), check)

  // -- signals: what an operation can be answered, read where it was performed

  private[onokay] sealed abstract class Signal
  private[onokay] final class FailSig(val e: Throwable) extends Signal
  private[onokay] object CancelSig extends Signal
  /** an abort to the handler `to` (its dispatch, one a run of the handler), its answer */
  private[onokay] final class AbortSig(val to: AnyRef, val answer: Any) extends Signal
  /** a value given inside a region a cancellation waits for: the region marked, then the value */
  private[onokay] final class PendingSig(val v: Any) extends Signal

  /** an operation's answer `v` read where it was performed: a signal unwinds from here, a value goes to `next` */
  private[onokay] def answered(v: Any)(next: Any => U): U = v match
    case p: PendingSig => bind(request(k => Mark(k)), _ => answered(p.v)(next))
    case s: Signal => unwind(s)
    case _ => next(v)
  private val check: Any => U = v => answered(v)(pureU)

  /** to the nearest delimiter taking it: `recover`, `ensuring`, a handler (its abort), a region, the top */
  private[onokay] def unwind(s: Signal): U = request(_ => Unwind(s))

  // -- the requests: what a delimiter's dispatch is given besides the body's value

  private[onokay] final class Done(val v: Any)
  /** the value a capture's hole is given when a request is forwarded: the program to run there */
  private final class Then(val c: U)
  private val runThen: Any => U = { case t: Then => t.c; case v => pureU(v) }

  private[onokay] sealed abstract class Req
  /** a request with the rest: forwarded, its `k` composed */
  private[onokay] sealed abstract class WithK extends Req:
    def k: Any => U
    def again(k: Any => U, d: Delim): WithK
  private[onokay] final class Perform(val op: Op[?], val k: Any => U, val region: Boolean) extends WithK:
    def again(k2: Any => U, d: Delim): WithK = Perform(op, k2, region || d.isInstanceOf[RegionD])
  private[onokay] final class Shift(val body: Fn[Fn[Any, Prog[Any]], Prog[Any]], val k: Any => U) extends WithK:
    def again(k2: Any => U, d: Delim): WithK = Shift(body, k2)
  /** to the nearest region: a cancellation pending there */
  private[onokay] final class Mark(val k: Any => U) extends WithK:
    def again(k2: Any => U, d: Delim): WithK = Mark(k2)
  /** the handlers in force, innermost first: each adds itself; the top adds the host */
  private[onokay] final class Env(val acc: List[Handler[?, ?]], val k: Any => U) extends WithK:
    def again(k2: Any => U, d: Delim): WithK = d match
      case h: HandlerD => Env(h.h :: acc, k2)
      case _ => Env(acc, k2)
  /** an unwinding: the rest dropped */
  private[onokay] final class Unwind(val s: Signal) extends Req

  /** the handlers in force where a foreign computation starts, and the host */
  private[onokay] final class EnvV(val handlers: List[Handler[?, ?]], val host: Answers)

  // -- the delimiters: each a dispatch, outside its `Reset`

  private[onokay] abstract class Delim extends (Any => U):
    final def apply(v: Any): U = v match
      case d: Done => done(d.v)
      case r: Req => req(r)
      case other => unwind(FailSig(IllegalStateException(s"not a request: $other")))
    def done(v: Any): U
    def req(r: Req): U = forward(r)
    /** `k`, this delimiter put back after it: the rest of the body, deep */
    final def again(k: Any => U): Any => U = y => bind(k(y), this)
    /** a request this delimiter does not take, on outward: an unwinding as it is, a request with `k` composed — the
     * rest up to here, this delimiter, the rest up to the next */
    final def forward(r: Req): U = r match
      case u: Unwind => request(_ => u)
      case w: WithK =>
        val kk = again(w.k)
        bind(request(k2 => w.again(y => k2(Then(kk(y))), this)), runThen)

  /** a run of a kernel handler */
  private final class HandlerD(val h: Handler[?, ?]) extends Delim:
    private val t: Type = h.effect()
    def done(v: Any): U = h match
      case r: Returning[?, ?] => try pureU(trust[Returning[Any, Any]](r).answer(v)) catch case NonFatal(e) => unwind(FailSig(e))
      case g: General[?, ?] => guard(trust[General[Any, Any]](g).ret(v, OnOkay))
      case _ => pureU(v)
    override def req(r: Req): U = r match
      case p: Perform if p.op.effect() eq t =>
        val k = again(p.k)
        h match
          case a: Answering[?, ?] => k(try a.value(trust[Op[Any]](p.op)) catch case NonFatal(e) => FailSig(e))
          case a: Aborting[?, ?] =>
            k(try AbortSig(this, trust[Aborting[Any, Any]](a).abort(trust[Op[Any]](p.op))) catch case NonFatal(e) => FailSig(e))
          case g: General[?, ?] => guard(trust[General[Any, Any]](g).apply[Any](trust[Op[Any]](p.op), x => P(k(x)), OnOkay))
          case _ => k(FailSig(IllegalStateException(s"a handler of no shape: $h")))
      case s: Shift => guard(s.body.apply(x => P(again(s.k)(x))))
      case u: Unwind => u.s match
        case a: AbortSig if a.to.eq(this) => pureU(a.answer)
        case _ => forward(u)
      case _ => forward(r)

  /** `body $ k` */
  private final class DollarD(k: Fn[Any, Prog[Any]]) extends Delim:
    def done(v: Any): U = guard(k.apply(v))
    override def req(r: Req): U = r match
      case s: Shift => guard(s.body.apply(x => P(again(s.k)(x))))
      case _ => forward(r)

  private final class RecoverD[A](h: Fn[Throwable, Prog[A]]) extends Delim:
    def done(v: Any): U = pureU(v)
    override def req(r: Req): U = r match
      case u: Unwind => u.s match
        case f: FailSig => guard(h.apply(f.e))
        case _ => forward(u)
      case _ => forward(r)

  private final class EnsuringD(fin: Program[Void]) extends Delim:
    def done(v: Any): U = bind(built(fin), _ => pureU(v))
    override def req(r: Req): U = r match
      case u: Unwind => bind(built(fin), _ => request(_ => u))
      case _ => forward(r)

  /** a region a cancellation waits for; `pending`, one waits — it cancels when the region ends, by a value or not */
  private final class RegionD(val pending: Boolean) extends Delim:
    def done(v: Any): U = if pending then unwind(CancelSig) else pureU(v)
    override def req(r: Req): U = r match
      case m: Mark => RegionD(pending = true).again(m.k)(())
      case u: Unwind if pending => unwind(CancelSig)
      case _ => forward(r)

  /** the program handed out: its end a step; an operation no handler took, the host's in place or a yield */
  private final class Top(host: Answers) extends Delim:
    def done(v: Any): U = pureU(new Step.Done[Any](v))
    override def req(r: Req): U = r match
      case u: Unwind => u.s match
        case f: FailSig => pureU(new Step.Failed[Any](f.e))
        case CancelSig => pureU(new Step.Cancelled[Any]())
        case _ => pureU(new Step.Failed[Any](IllegalStateException("an abort with its handler gone")))
      case p: Perform =>
        val answers = try host.answers(p.op.effect()) catch case NonFatal(_) => false
        if answers then again(p.k)(try host.value(trust[Op[Any]](p.op)) catch case NonFatal(e) => FailSig(e))
        else pureU(new Step.Yield[Any, Any](trust[Op[Any]](p.op), resumption(p.op, again(p.k), p.region, pending = false)))
      case s: Shift => again(s.k)(FailSig(IllegalStateException("shift0 with no delimiter around it")))
      case m: Mark => again(m.k)(())
      case e: Env => again(e.k)(EnvV(e.acc.reverse, host))

  /** `k` of an operation handed out, as the protocol's: each call a run of okay's machine. Inside a region a
   * cancellation waits: the same operation again, the cancellation pending — each answer given then marks it */
  private def resumption(op: Op[?], k: Any => U, region: Boolean, pending: Boolean): Resume[Any, Any] = new Resume[Any, Any]:
    private def on(v: Any): Step[Any] = run(k(if pending then PendingSig(v) else v))
    def resume(x: Any): Step[Any] = on(x)
    def fail(e: Throwable): Step[Any] = on(FailSig(e))
    def cancel(): Step[Any] =
      if region then new Step.Yield[Any, Any](trust[Op[Any]](op), resumption(op, k, region, pending = true))
      else run(k(CancelSig))

  /** a run of okay's machine to its step */
  private def run(u: U): Step[Any] =
    try Machine.value(u) match
      case s: Step[?] => trust[Step[Any]](s)
      case other => new Step.Failed[Any](IllegalStateException(s"not a step: $other"))
    catch case NonFatal(e) => new Step.Failed[Any](e)

  // -- the protocol

  /** EXPORT: the program under the top delimiter, on okay's machine */
  def computation[A](program: Program[A], row: java.util.Set[Type]): Computation[A] =
    val fixed = java.util.Collections.unmodifiableSet(new java.util.HashSet[Type](row))
    new Computation[A]:
      def start(host: Answers): Step[A] = trust[Step[A]](run(delimit(built(program), Top(host))))
      def effects(): java.util.Set[Type] = fixed

  /** what a foreign computation's operation is answered in place by here: the nearest handler of its effect, if it
   * answers in place; none, the host */
  private def answersFrom(env: EnvV): Answers = new Answers:
    private def nearest(t: Type): Handler[?, ?] | Null = env.handlers.find(_.effect() eq t).orNull
    def answers(t: Type): Boolean = nearest(t) match
      case null => env.host.answers(t)
      case h => h.isInstanceOf[Answering[?, ?]]
    def value[X](op: Op[X]): X = nearest(op.effect()) match
      case null => env.host.value(op)
      case a: Answering[?, ?] => trust[X](a.value(op))
      case _ => throw IllegalStateException(s"not answered in place: $op")

  private def safely(s: => Step[?]): Step[?] = try s catch case NonFatal(e) => new Step.Failed[Any](e)

  /** IMPORT: a foreign computation's steps, taken here — its operation performed here, the answer (a value, a
   * failure) given to its `k`; an abort or a cancellation crossing it cancels it, its finalizers' operations answered
   * here, then goes on. `after`: the signal it is being cancelled for — its last step, whatever it is, gives way to it */
  private def drive(s: Step[?], after: Signal | Null): U = s match
    case y: Step.Yield[?, ?] =>
      val k = trust[Resume[Any, Any]](y.k())
      bind(request(kk => Perform(y.op(), kk, region = false)), v => answerForeign(k, v, after))
    case _ if after != null => unwind(after)
    case d: Step.Done[?] => pureU(d.value())
    case f: Step.Failed[?] => unwind(FailSig(f.error()))
    case _: Step.Cancelled[?] => unwind(CancelSig)

  private def answerForeign(k: Resume[Any, Any], v: Any, after: Signal | Null): U = v match
    case f: FailSig => drive(safely(k.fail(f.e)), after)
    case p: PendingSig => bind(request(kk => Mark(kk)), _ => answerForeign(k, p.v, after))
    case s: Signal => drive(safely(k.cancel()), if after != null then after else s)
    case _ => drive(safely(k.resume(v)), after)

  /** run a program with nothing left to perform: its value, or its failure thrown */
  def runValue[A](p: Program[A]): A = computation(p, java.util.Collections.emptySet[Type]()).start(Answers.NONE) match
    case d: Step.Done[A @unchecked] => d.value()
    case f: Step.Failed[A @unchecked] => throw f.error()
    case _: Step.Cancelled[A @unchecked] => throw IllegalStateException("cancelled")
    case y: Step.Yield[?, ?] => throw IllegalStateException(s"an operation with no handler: ${y.op()}")
