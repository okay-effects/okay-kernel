package okay.lexical

import okay.kernel.{Effect, Fn, Handler, Monad, Op, Program, Steps}
import okay.kernel.Prog as KProg
import okay.std.{Handlers, JavaHandlers}
import okay.kernel.std.{Effects, Final, Get, Logged, Put, Raise, Result, Shift, Shift0, Tell}

/**
 * THE KERNEL TYPED BY LEVELS, NOT ROWS: for Java and Scala 2, whose types have no rows. A program is at a level `L`;
 * a handler's body is a generic method, `in[M <: L]`, so its level `M` is fresh: a capability, given at `M`, does
 * not leave the body — statically (okay-java's levels). Capabilities are LEXICAL: each run of a handler an effect of
 * its own (`Effect(name)`, generative), so a capability reaches its own handler, past any other of its kind nearer.
 * `Prog` is the kernel's `Program`: run on any monad, mixed with programs of any language.
 */
final class Prog[L, A] private (private val p: Program[A]) extends Program[A]:
  def in(m: Monad): KProg[A] = p.in(m)
  /** `f` on this program's value: a program of this level, or of one further out (`? super L` in Java) */
  def flatMap[B](f: Fn[A, Prog[? >: L, B]]): Prog[L, B] =
    Prog(m => m.flatMap(p.in(m), a => f(a).in(m)))
  def map[B](f: Fn[A, B]): Prog[L, B] = Prog(m => m.map(p.in(m), f))
  def andThen[B](next: Prog[? >: L, B]): Prog[L, B] = flatMap(_ => next)
  /** this program at a level further in */
  def at[M <: L]: Prog[M, A] = this.asInstanceOf[Prog[M, A]]

object Prog:
  def pure[L, A](a: A): Prog[L, A] = Prog(m => m.pure(a))
  def delay[L, A](c: okay.kernel.Thunk[Prog[L, A]]): Prog[L, A] = Prog(m => m.delay(() => c.get().in(m)))
  /** a program of another language, at a level said */
  def of[L, A](p: Program[A]): Prog[L, A] = Prog(p)
  /** a program at the top, run: by the machine of steps, or another monad */
  def run[A](p: Prog[Top, A]): A = Program.run(p, Steps)
  def run[A](p: Prog[Top, A], m: Monad): A = Program.run(p, m)

  /** a handler of a fresh effect, one's own: its handler made for the effect, the body given the capability */
  def handle[L, A, R](handler: Fn[Effect, Handler[A, R]], body: Body[L, A]): Prog[L, R] =
    Prog(m => {
      val e = Effect("lexical")
      m.handle(handler(e), body.in(Cap(e)))
    })

  /** a delimiter of a fresh prompt, its body given the prompt: its body's value the answer */
  def reset[L, R](body: ResetBody[L, R]): Prog[L, R] =
    Prog(m => {
      val e = Effect("Prompt")
      m.handle(okay.std.delimiter[R](e), body.in[L](Prompt[L, L, R](e)))
    })

  /** a log of `W`s: the log, first told first, and the value */
  def writer[L, W, A](body: WriterBody[L, W, A]): Prog[L, Logged[W, A]] =
    Prog(m => {
      val e = Effect("Writer")
      m.handle(JavaHandlers.writer[W, A](e), body.in(WriterCap(e)))
    })

  /** a state of a cell, one a run: the final state and the value */
  def state[L, S, A](s0: S, body: StateBody[L, S, A]): Prog[L, Final[S, A]] =
    Prog(m => {
      val e = Effect("State")
      m.handle(JavaHandlers.state[S, A](e, s0), body.in(StateCap(e)))
    })

  /** the value, or the first failure raised */
  def attempt[L, E, A](body: ThrowsBody[L, E, A]): Prog[L, Result[E, A]] =
    Prog(m => {
      val e = Effect("Throws")
      m.handle(JavaHandlers.throws[E, A](e), body.in(ThrowsCap(e)))
    })

  private final class WriterCap[M, W](e: Effect) extends Writer[M, W]:
    def tell(w: W): Prog[M, Void] = Prog(m => m.perform(Tell(w, e)))
  private final class StateCap[M, S](e: Effect) extends State[M, S]:
    def get(): Prog[M, S] = Prog(m => m.perform(Get[S](e)))
    def put(s: S): Prog[M, Void] = Prog(m => m.perform(Put(s, e)))
  private final class ThrowsCap[M, E](e: Effect) extends Throws[M, E]:
    def raise[X](err: E): Prog[M, X] = Prog(m => m.perform(Raise[E, X](err, e)))

/** the level of the top */
sealed trait Top

/** a handler's body: a generic method, its level `M` fresh — given the capability at `M`, which names it only */
trait Body[L, A]:
  def in[M <: L](cap: Cap[M]): Prog[M, A]
trait WriterBody[L, W, A]:
  def in[M <: L](w: Writer[M, W]): Prog[M, A]
trait StateBody[L, S, A]:
  def in[M <: L](s: State[M, S]): Prog[M, A]
trait ThrowsBody[L, E, A]:
  def in[M <: L](t: Throws[M, E]): Prog[M, A]

/** one's own effect's capability: an operation of it, built for its effect */
final class Cap[L] private[lexical] (e: Effect):
  def perform[X](op: Fn[Effect, Op[X]]): Prog[L, X] = Prog.of(m => m.perform(op(e)))
  def at[M <: L]: Cap[M] = this.asInstanceOf[Cap[M]]

trait Writer[L, W]:
  def tell(w: W): Prog[L, Void]
  def at[M <: L]: Writer[M, W] = this.asInstanceOf[Writer[M, W]]
trait State[L, S]:
  def get(): Prog[L, S]
  def put(s: S): Prog[L, Void]
  def at[M <: L]: State[M, S] = this.asInstanceOf[State[M, S]]
trait Throws[L, E]:
  def raise[X](e: E): Prog[L, X]
  def at[M <: L]: Throws[M, E] = this.asInstanceOf[Throws[M, E]]


/** a reset's body: a generic method, its level `M` fresh — given the prompt, at `M` */
trait ResetBody[L, R]:
  def in[M <: L](p: Prompt[L, M, R]): Prog[M, R]

/** A RESET'S PROMPT, at the body's level `M`, the reset's `L`, its answer `R`: shift0 and shift to it, by identity —
 * past any reset nearer */
final class Prompt[L, M, R] private[lexical] (e: Effect):
  /** the rest up to this reset, the reset included, to the clause, run in its place — outside it */
  def shift0[X](clause: Fn[Fn[X, Prog[L, R]], Prog[L, R]]): Prog[M, X] =
    Prog.of(m => m.perform(Shift0[X, R](e, k => clause(x => Prog.of[L, R](_ => k(x))))))
  /** the same, the clause under the reset again (Danvy–Filinski) */
  def shift[X](clause: Fn[Fn[X, Prog[L, R]], Prog[M, R]]): Prog[M, X] =
    Prog.of(m => m.perform(Shift[X, R](e, k => clause(x => Prog.of[L, R](_ => k(x))))))
