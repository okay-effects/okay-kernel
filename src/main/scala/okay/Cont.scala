package okay

import okay.kernel.{Effect, Fn, Handler, Monad, Op, Prog, Program, Steps}

/**
 * DELIMITED CONTROL, THE ANSWER TYPE MODIFIED, STATICALLY — one type for all of it: Materzok–Biernacki's shift0 and
 * `$` (reset0 the `$` of no return), a program's answer moving from `I` to `O` in its type, the answers themselves
 * programs (the CPS hierarchy); and Danvy–Filinski's shift and reset on them (`shift k. e` is `shift0 k. reset0 e`),
 * typed as Asai–Kameyama's at answers of no move, `Cont.Flat[I, O, A]`. One delimiter for both: one program may
 * shift and shift0. On the kernel, a view of a `Program`: every delimiter a handler of one effect, so a shift reaches
 * the nearest. Its signatures plain generics and `Fn`: Java writes it as Scala does
 */
final class Cont[I, O, A] private[okay] (private[okay] val p: Program[A]):
  def flatMap[I2, B](f: Fn[A, Cont[I2, I, B]]): Cont[I2, O, B] =
    Cont(m => m.flatMap(p.in(m), a => f(a).p.in(m)))
  def map[B](f: Fn[A, B]): Cont[I, O, B] = Cont(m => m.map(p.in(m), f))

object Cont:
  private val effect: Effect = Effect.of("Reset0")
  private final case class Shift0Op[A](f: Fn[Fn[A, Prog[Any]], Program[Any]]) extends Op[A]:
    def effect: Effect = Cont.effect

  private def delimiter[A](back: A => Program[Any]): Handler[A, Any] = new Handler[A, Any]:
    def effect: Effect = Cont.effect
    def ret(a: A, m: Monad): Prog[Any] = back(a).in(m)
    def apply[X](op: Op[X], k: Fn[X, Prog[Any]], m: Monad): Prog[Any] = op match
      case s: Shift0Op[X @unchecked] => s.f(k).in(m)

  private def inner[A](c: Cont[?, ?, ?]): Program[A] = c.p.asInstanceOf[Program[A]]

  def pure[S, A](a: A): Cont[S, S, A] = Cont(m => m.pure(a))

  /** A PROGRAM ANSWERING `R`: its value an `R`, its answer an `R`, unmoved — what a continuation `k(x)` gives that
   * moves no answer, the usual; `Cont.Flat[I, O, A]` is `Cont[Answer[I], Answer[O], A]` */
  type Answer[R] = Cont[R, R, R]

  /** THE DELIMITERS OF ANSWER `R`: their operations, `R` given once — at a call, only the operation's value, `A`
   * (`k` takes an `A`: no lambda tells it) */
  final class At[R] private[Cont] ():
    /** shift0, the answer kept: `k` and the clause at `R` */
    def shift0[A](f: Fn[Fn[A, Answer[R]], Answer[R]]): Cont[Answer[R], Answer[R], A] = Cont.shift0[R, R, R, Answer[R], A](f)
    /** shift0, the answer moved to `O`: `k` at `R`, the clause's answer an `O` */
    def shift0To[A, O](f: Fn[Fn[A, Answer[R]], O]): Cont[Answer[R], O, A] = Cont.shift0[R, R, R, O, A](f)
    /** a delimiter: the body's value the answer */
    def reset0(body: Cont[Answer[R], Answer[R], R]): Answer[R] = Cont.reset0[R, R, R, R, R](body)
    /** `ret $ body`, the body's value of type `A` to the answer through `ret` */
    def dollar[A](ret: Fn[A, Answer[R]], body: Cont[Answer[R], Answer[R], A]): Answer[R] = Cont.dollar[Answer[R], R, R, R, A](ret, body)
    def pure[A](a: A): Cont[R, R, A] = Cont.pure[R, A](a)
    /** shift, the answer kept */
    def shift[A](f: Fn[Fn[A, Flat[R, R, R]], Flat[R, R, R]]): Flat[R, R, A] = Cont.shift[R, R, A](f)
    /** shift, the answer moved from `R` to `O`: `k` answering `R` at the hole, the clause at `O` */
    def shiftTo[A, O](f: Fn[Fn[A, Flat[O, O, R]], Flat[O, O, O]]): Flat[R, O, A] = Cont.shift[R, O, A](f)
    /** a reset: the body at the answer `R`, its value; the reset answers `O`, what the body moved it to */
    def reset[O](body: Flat[R, O, R]): Flat[O, O, O] = Cont.reset[O, R, O](body)

  /** the delimiters of answer `R`: `Cont.at[Int]`, Java's `Cont.<Integer>at()` */
  def at[R]: At[R] = At[R]()
  /** the rest up to the nearest delimiter, the delimiter included, to `f`, run in its place: its answer `O` */
  def shift0[I1, O1, X1, O, A](f: Fn[Fn[A, Cont[I1, O1, X1]], O]): Cont[Cont[I1, O1, X1], O, A] =
    Cont(m => m.perform(Shift0Op[A](k => inner(f(a => Cont(_ => k(a).asInstanceOf[Prog[X1]])).asInstanceOf[Cont[?, ?, ?]]))))
  /** `ret $ body`: `body` under a delimiter, its value to `ret` */
  def dollar[I, I2, O2, B, A](ret: Fn[A, I], body: Cont[I, Cont[I2, O2, B], A]): Cont[I2, O2, B] =
    Cont(m => m.handle(delimiter[A](a => inner(ret(a).asInstanceOf[Cont[?, ?, ?]])), body.p).asInstanceOf[Prog[B]])
  /** `body` under a delimiter, its value the answer */
  def reset0[J, I2, O2, B, A](body: Cont[Cont[J, J, A], Cont[I2, O2, B], A]): Cont[I2, O2, B] =
    Cont(m => m.handle(delimiter[A](a => (pm: Monad) => pm.pure[Any](a)), body.p).asInstanceOf[Prog[B]])
  /** a program closed, run: its answer */
  def value[A](c: Cont[A, A, A]): A = Program.run(c.p, Steps)
  def value[A](c: Cont[A, A, A], m: Monad): A = Program.run(c.p, m)
  /** a closed program as a kernel `Program`: under any handler, in any language */
  def program[A](c: Cont[A, A, A]): Program[A] = c.p

  // DANVY–FILINSKI'S, ON THE SAME DELIMITERS

  /** a program of value `A` which, its rest up to the nearest reset answering `I`, answers `O`: Asai–Kameyama's
   * typing, the answers plain types — `Cont` at answers of no move */
  type Flat[I, O, A] = Cont[Answer[I], Answer[O], A]

  /** `shift(k => body)`: `k` the rest up to the nearest reset, the reset included, answering `I` at the hole; the
   * body under the reset again, at its final answer `O` — the answer moved from `I` to `O` */
  def shift[I, O, A](f: Fn[Fn[A, Flat[O, O, I]], Flat[O, O, O]]): Flat[I, O, A] =
    shift0[I, I, I, Answer[O], A](k => reset0[O, O, O, O, O](
      f(a => closed[O, I](k(a))).asInstanceOf[Cont[Answer[O], Answer[O], O]]))

  /** `reset(body)`: the body at the answer `S`, its value; the reset answers what the body moved it to, `O` — and
   * as a program it is closed: at any answer `T` around it */
  def reset[T, S, O](body: Flat[S, O, S]): Flat[T, T, O] =
    closed[T, O](reset0[S, O, O, O, S](body.asInstanceOf[Cont[Answer[S], Answer[O], S]]))

  /** a program whose captures cannot leave it — a reset, a continuation `k(x)`, holding its delimiter — at any
   * answer around it: the run the same, the type moved */
  def closed[T, A](c: Cont[?, ?, A]): Flat[T, T, A] = c.asInstanceOf[Flat[T, T, A]]

  /** a flat program's value, now: a reset, or `k(x)` — it holds its delimiter, so it runs on its own
   * (Asai–Kameyama's pure `k`) */
  def eval[T, I](c: Flat[T, T, I]): I = Program.run(c.p, Steps)
  def eval[T, I](c: Flat[T, T, I], m: Monad): I = Program.run(c.p, m)
