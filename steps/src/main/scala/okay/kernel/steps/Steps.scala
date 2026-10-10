package okay.kernel.steps

import okay.kernel._

/**
 * THE KERNEL'S REFERENCE MACHINE: `Effects` and the protocol, simple and exact rather than fast. A program is nodes;
 * the run one loop over them with the frames as an immutable list, the innermost first — so a captured `k` is a
 * prefix of it, multi-shot for free, resumable from any thread. Every delimiter a frame: a handler's, a `$`'s; and
 * the frames of failures, finalizers and regions cancellation waits for. An operation goes to the nearest handler of
 * its effect: answered in place (`Answering`), ended (`Aborting`, its finalizers run), or with `k` (`General`); with
 * none, to the host in place (`Answers`), else handed out (`Step.Yield`). Another implementation's program comes in
 * as a node (`foreign`), its yields answered here.
 *
 * Values flow through the frames untyped: the one trusted step is `trust`, where a value is given to the function the
 * program bound it to — the types agree because the program was built typed and an operation's handler is found by
 * its effect's exact identity. In the subset Scala 2.13 and 3 share, on every platform.
 */
object Steps extends Effects {

  // -- the programs: nodes, the value's type a phantom

  private[steps] sealed abstract class Node[A] extends Prog[A]
  private final class PureN[A](val a: A) extends Node[A]
  private final class BindN[X, A](val p: Node[X], val f: Fn[X, Prog[A]]) extends Node[A]
  private final class DelayN[A](val t: Thunk[Prog[A]]) extends Node[A]
  private final class PerformN[X](val op: Op[X]) extends Node[X]
  private final class HandleN[A, R](val h: Handler[A, R], val body: Program[A]) extends Node[R]
  private final class Shift0N[A, I, O](val body: Fn[Fn[A, Prog[I]], Prog[O]]) extends Node[A]
  private final class DollarN[A, I, O](val body: Program[A], val k: Fn[A, Prog[I]]) extends Node[O]
  private final class FailedN[A](val e: Throwable) extends Node[A]
  private final class RecoverN[A](val body: Program[A], val h: Fn[Throwable, Prog[A]]) extends Node[A]
  private final class EnsuringN[A](val body: Program[A], val fin: Program[Void]) extends Node[A]
  private final class UncancelableN[A](val body: Program[A]) extends Node[A]
  private final class ForeignN[A](val c: Computation[A]) extends Node[A]
  /** a captured rest resumed: its frames back in front, the value (or the failure) given to them */
  private final class ResumeN[A](val frames: List[Frame], val value: Any, val error: Throwable) extends Node[A]

  def pure[A](a: A): Prog[A] = new PureN(a)
  def flatMap[A, B](p: Prog[A], f: Fn[A, Prog[B]]): Prog[B] = new BindN(node(p), f)
  def delay[A](p: Thunk[Prog[A]]): Prog[A] = new DelayN(p)
  def perform[X](op: Op[X]): Prog[X] = new PerformN(op)
  def handle[A, R](h: Handler[A, R], body: Program[A]): Prog[R] = new HandleN(h, body)
  def shift0[A, I, O](body: Fn[Fn[A, Prog[I]], Prog[O]]): Prog[A] = new Shift0N(body)
  def dollar[A, I, O](body: Program[A], k: Fn[A, Prog[I]]): Prog[O] = new DollarN[A, I, O](body, k)
  def failed[A](e: Throwable): Prog[A] = new FailedN(e)
  def recover[A](body: Program[A], h: Fn[Throwable, Prog[A]]): Prog[A] = new RecoverN(body, h)
  def ensuring[A](body: Program[A], finalizer: Program[Void]): Prog[A] = new EnsuringN(body, finalizer)
  def uncancelable[A](body: Program[A]): Prog[A] = new UncancelableN(body)

  /** another implementation's program, as one of this machine: its yields answered here */
  def foreign[A](c: Computation[A]): Prog[A] = new ForeignN(c)

  private def node[A](p: Prog[A]): Node[A] = p match {
    case n: Node[A @unchecked] => n
    case _ => throw new IllegalArgumentException("not a program of this machine: " + p)
  }

  /** THE TRUSTED STEP: a value given where the program, built typed, expects it */
  private def trust[T](v: Any): T = v.asInstanceOf[T]

  // -- the frames: the rest of the run, innermost first

  private[steps] sealed abstract class Frame
  private final class FBind(val f: Fn[Any, Prog[Any]]) extends Frame
  private final class FHandler(val h: Handler[Any, Any]) extends Frame
  private final class FDollar(val k: Fn[Any, Prog[Any]]) extends Frame
  private final class FRecover(val h: Fn[Throwable, Prog[Any]]) extends Frame
  private final class FEnsuring(val fin: Program[Void]) extends Frame
  private object FUncancelable extends Frame
  /** a finalizer running: when it ends, the outcome it interrupted goes on */
  private final class FRestore(val outcome: Outcome) extends Frame
  /** another implementation's program waiting on an operation answered here: its `k` */
  private final class FForeign(val k: Resume[Any, Any]) extends Frame
  /** a foreign program being cancelled, its finalizers' steps answered here: when it ends, `outcome` goes on */
  private final class FAfter(val outcome: Outcome) extends Frame

  /** where the loop is: a node to run, a value, a failure, a cancellation */
  private sealed abstract class Outcome
  private final class Eval(val n: Node[?]) extends Outcome
  private final class Ret(val v: Any) extends Outcome
  private final class Thrown(val e: Throwable) extends Outcome
  private object Cancelling extends Outcome

  /** a handler's abort, unwinding to it — its finalizers run, no `recover` catches it */
  private final class Abort(val to: FHandler, val answer: Any) extends RuntimeException(null, null, false, false)

  private def isDelimiter(f: Frame): Boolean = f match {
    case _: FHandler | _: FDollar => true
    case _ => false
  }

  private def build[A](p: Program[A]): Node[A] = node(p.in(this))

  /** a user's function, its failure the program's */
  private def guarded(f: => Node[?]): Outcome =
    try new Eval(f) catch { case e: Throwable if nonFatal(e) => new Thrown(e) }

  private def nonFatal(e: Throwable): Boolean = scala.util.control.NonFatal(e)

  // -- the loop

  /** run from `start` with the frames `stack`, the host answering in place what nothing here does */
  private def loop(start: Outcome, stack0: List[Frame], host: Answers, cancelPending0: Boolean): Step[Any] = {
    var out: Outcome = start
    var stack: List[Frame] = stack0
    var cancelPending = cancelPending0
    while (true) {
      out match {
        case ev: Eval => ev.n match {
          case p: PureN[?] => out = new Ret(p.a)
          case b: BindN[?, ?] =>
            stack = new FBind(trust[Fn[Any, Prog[Any]]](b.f)) :: stack
            out = new Eval(b.p)
          case d: DelayN[?] => out = guarded(node(d.t.get()))
          case p: PerformN[?] => out = perform(trust[Op[Any]](p.op), stack, host) match {
            case r: Performed => stack = r.stack; r.out
            case y: Suspend => return new Step.Yield[Any, Any](y.op, resumption(y.op, stack, host, cancelPending))
          }
          case h: HandleN[?, ?] =>
            stack = new FHandler(trust[Handler[Any, Any]](h.h)) :: stack
            out = guarded(build(h.body))
          case s: Shift0N[?, ?, ?] =>
            val i = stack.indexWhere(isDelimiter)
            if (i < 0) out = new Thrown(new IllegalStateException("shift0 with no delimiter around it"))
            else {
              val captured = stack.take(i + 1)
              stack = stack.drop(i + 1)
              val k = new Fn[Any, Prog[Any]] { def apply(x: Any): Prog[Any] = new ResumeN[Any](captured, x, null) }
              out = guarded(node(trust[Fn[Fn[Any, Prog[Any]], Prog[Any]]](s.body).apply(k)))
            }
          case d: DollarN[?, ?, ?] =>
            stack = new FDollar(trust[Fn[Any, Prog[Any]]](d.k)) :: stack
            out = guarded(build(d.body))
          case f: FailedN[?] => out = new Thrown(f.e)
          case r: RecoverN[?] =>
            stack = new FRecover(trust[Fn[Throwable, Prog[Any]]](r.h)) :: stack
            out = guarded(build(r.body))
          case e: EnsuringN[?] =>
            stack = new FEnsuring(e.fin) :: stack
            out = guarded(build(e.body))
          case u: UncancelableN[?] =>
            stack = FUncancelable :: stack
            out = guarded(build(u.body))
          case r: ResumeN[?] =>
            stack = r.frames ::: stack
            out = if (r.error != null) new Thrown(r.error) else new Ret(r.value)
          case f: ForeignN[?] =>
            out = foreignStep(trust[Computation[Any]](f.c).start(answersHere(stack, host)), stack) match {
              case fs: ForeignNext => stack = fs.stack; fs.out
            }
        }

        case r: Ret => stack match {
          case Nil => return new Step.Done[Any](r.v)
          case top :: rest =>
            stack = rest
            top match {
              case b: FBind => out = guarded(node(b.f.apply(r.v)))
              case h: FHandler => h.h match {
                case ret: Returning[Any, Any] @unchecked =>
                  out = try new Ret(ret.answer(r.v)) catch { case e: Throwable if nonFatal(e) => new Thrown(e) }
                case g: General[Any, Any] @unchecked => out = guarded(node(g.ret(r.v, this)))
                case _ => out = r
              }
              case d: FDollar => out = guarded(node(d.k.apply(r.v)))
              case _: FRecover => out = r
              case e: FEnsuring =>
                stack = new FRestore(r) :: stack
                out = guarded(build(e.fin))
              case FUncancelable =>
                if (cancelPending) { cancelPending = false; out = Cancelling } else out = r
              case fr: FRestore => out = fr.outcome
              case fa: FAfter => out = fa.outcome
              case f: FForeign =>
                foreignStep(f.k.resume(r.v), stack) match { case fs: ForeignNext => stack = fs.stack; out = fs.out }
            }
        }

        case t: Thrown => stack match {
          case Nil => t.e match {
            case a: Abort => return new Step.Failed[Any](new IllegalStateException("an abort with its handler gone", a))
            case e => return new Step.Failed[Any](e)
          }
          case top :: rest =>
            stack = rest
            (top, t.e) match {
              case (h: FHandler, a: Abort) if h eq a.to => out = new Ret(a.answer)
              case (r: FRecover, e) if !e.isInstanceOf[Abort] => out = guarded(node(r.h.apply(e)))
              case (e: FEnsuring, _) =>
                stack = new FRestore(t) :: stack
                out = guarded(build(e.fin))
              case (fr: FRestore, _) => out = t // a finalizer failed: its failure goes on, the one it interrupted lost
              case (fa: FAfter, _) => out = fa.outcome // a foreign program's cancellation failed: the outcome it was for goes on
              case (FUncancelable, _) if cancelPending => cancelPending = false; out = Cancelling // the region ended, failed: the cancellation waiting goes on
              case (f: FForeign, e) if !e.isInstanceOf[Abort] =>
                foreignStep(f.k.fail(e), stack) match { case fs: ForeignNext => stack = fs.stack; out = fs.out }
              case (f: FForeign, _) =>
                // an abort crossing it: it is cancelled, its finalizers' operations answered here, then the abort goes on
                foreignStep(f.k.cancel(), new FAfter(t) :: stack) match { case fs: ForeignNext => stack = fs.stack; out = fs.out }
              case _ => out = t
            }
        }

        case Cancelling => stack match {
          case Nil => return new Step.Cancelled[Any]()
          case top :: rest =>
            stack = rest
            top match {
              case e: FEnsuring =>
                stack = new FRestore(Cancelling) :: stack
                out = guarded(build(e.fin))
              case f: FForeign =>
                // it is cancelled too, its finalizers' operations answered here, then this cancellation goes on
                foreignStep(f.k.cancel(), new FAfter(Cancelling) :: stack) match { case fs: ForeignNext => stack = fs.stack; out = fs.out }
              case fa: FAfter => out = fa.outcome
              case _ => out = Cancelling
            }
        }
      }
    }
    throw new IllegalStateException("unreachable")
  }

  // -- an operation

  private sealed abstract class PerformResult
  private final class Performed(val stack: List[Frame], val out: Outcome) extends PerformResult
  private final class Suspend(val op: Op[Any]) extends PerformResult

  private def perform(op: Op[Any], stack: List[Frame], host: Answers): PerformResult = {
    val e = op.effect()
    val i = stack.indexWhere {
      case h: FHandler => h.h.effect() eq e
      case _ => false
    }
    if (i < 0) {
      if (host.answers(e)) new Performed(stack, try new Ret(host.value(op)) catch { case t: Throwable if nonFatal(t) => new Thrown(t) })
      else new Suspend(op)
    } else {
      val frame = trust[FHandler](stack(i))
      frame.h match {
        case a: Answering[Any, Any] @unchecked =>
          new Performed(stack, try new Ret(a.value(op)) catch { case t: Throwable if nonFatal(t) => new Thrown(t) })
        case a: Aborting[Any, Any] @unchecked =>
          new Performed(stack, try new Thrown(new Abort(frame, a.abort(op))) catch { case t: Throwable if nonFatal(t) => new Thrown(t) })
        case g: General[Any, Any] @unchecked =>
          val captured = stack.take(i + 1)
          val k = new Fn[Any, Prog[Any]] { def apply(x: Any): Prog[Any] = new ResumeN[Any](captured, x, null) }
          new Performed(stack.drop(i + 1), guarded(node(g.apply(op, k, this))))
        case h => new Performed(stack, new Thrown(new IllegalStateException("a handler of no shape: " + h)))
      }
    }
  }

  // -- the protocol

  /** what a foreign program's operation is answered in place by here: the nearest handler of its effect answering
   * in place, or — none here — the host */
  private def answersHere(stack: List[Frame], host: Answers): Answers = new Answers {
    private def nearest(e: Type): Handler[Any, Any] = stack.collectFirst { case h: FHandler if h.h.effect() eq e => h.h }.orNull
    def answers(e: Type): Boolean = nearest(e) match {
      case null => host.answers(e)
      case _: Answering[?, ?] => true
      case _ => false
    }
    def value[X](op: Op[X]): X = nearest(op.effect()) match {
      case null => host.value(op)
      case a: Answering[Any, Any] @unchecked => a.value(op)
      case h => throw new IllegalStateException("not answered in place: " + op)
    }
  }

  private final class ForeignNext(val stack: List[Frame], val out: Outcome)

  /** a foreign program's step, taken here: a value goes on, a failure is thrown here, a cancellation cancels this
   * path, an operation is performed here — its answer to the foreign `k` (the frame `FForeign`) */
  private def foreignStep(s: Step[Any], stack: List[Frame]): ForeignNext = s match {
    case d: Step.Done[Any @unchecked] => new ForeignNext(stack, new Ret(d.value()))
    case f: Step.Failed[Any @unchecked] => new ForeignNext(stack, new Thrown(f.error()))
    case _: Step.Cancelled[Any @unchecked] => new ForeignNext(stack, Cancelling)
    case y: Step.Yield[?, ?] =>
      new ForeignNext(new FForeign(trust[Resume[Any, Any]](y.k())) :: stack, new Eval(new PerformN(trust[Op[Any]](y.op()))))
  }

  /** the rest of a run suspended on `op`, handed out. A cancellation inside a region it waits for: the same wait
   * again, the cancellation pending — the region's end cancels */
  private def resumption(op: Op[Any], stack: List[Frame], host: Answers, cancelPending: Boolean): Resume[Any, Any] = new Resume[Any, Any] {
    def resume(x: Any): Step[Any] = loop(new Ret(x), stack, host, cancelPending)
    def fail(e: Throwable): Step[Any] = loop(new Thrown(e), stack, host, cancelPending)
    def cancel(): Step[Any] =
      if (stack.contains(FUncancelable)) new Step.Yield[Any, Any](op, resumption(op, stack, host, true))
      else loop(Cancelling, stack, host, false)
  }

  /** A PROGRAM OF THIS MACHINE, HANDED OUT: its row given, as it is known statically where it was written */
  def computation[A](p: Program[A], row: java.util.Set[Type]): Computation[A] = {
    val fixed = java.util.Collections.unmodifiableSet(new java.util.HashSet[Type](row))
    new Computation[A] {
      def start(host: Answers): Step[A] = trust[Step[A]](loop(guarded(build(p)), Nil, host, false))
      def effects(): java.util.Set[Type] = fixed
    }
  }

  /** run a program with nothing left to perform: its value, or its failure thrown */
  def run[A](p: Program[A]): A = computation(p, java.util.Collections.emptySet[Type]()).start(Answers.NONE) match {
    case d: Step.Done[A @unchecked] => d.value()
    case f: Step.Failed[A @unchecked] => throw f.error()
    case _: Step.Cancelled[A @unchecked] => throw new IllegalStateException("cancelled")
    case y: Step.Yield[?, ?] => throw new IllegalStateException("an operation with no handler: " + y.op())
  }
}
