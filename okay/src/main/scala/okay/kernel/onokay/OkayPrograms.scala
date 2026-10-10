package okay.kernel.onokay

import okay.cont.{Cap, Clause, Cont, Ctx, Free, Has, In, Member, Pure, Reach, Reaches, Root, Row, Target, +:}
import okay.kernel.{Answers, Computation, Op, Program, Resume, Same, Step, Type}
import okay.kernel.std.{Choose as KChoose, Reader as KReader, State as KState, Throws as KThrows, Writer as KWriter}
import OnOkay.{FailSig, Perform, U, answered, bind, delimit, level, pureU, trust, unwind}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/**
 * HOW ONE OF OKAY'S EFFECTS `E` IS SEEN BY THE KERNEL: its kernel `Type`; its operations as the kernel's, the kernel's
 * answer as okay's (export); the kernel's operations of that type as okay's, okay's answer as the kernel's (import).
 * The standard ones below: okay-cont's `State`, `Reader`, `Writer`, `Throws`, `Choose` as the kernel's vocabulary
 */
trait Mapping[E[+_]]:
  def effect: Type
  def toKernel[X](op: E[X]): Op[?]
  /** the kernel's answer to `toKernel(op)`, as `op`'s */
  def answer[X](op: E[X], v: Any): X
  /** a kernel operation of `effect` as okay's, with okay's answer back as the kernel's */
  def fromKernel(op: Op[?]): Okayed[E, ?]

/** an operation of okay's, from a kernel one: and okay's answer to it as the kernel's */
final class Okayed[E[+_], X](val op: E[X], val back: X => Any)

object Mapping:
  import okay.cont.{Choose, Reader, State, Throws, Writer}

  /** okay's `State % S` as the kernel's state of the type `t` (`okay.kernel.std.State.of(tag)`) */
  def state[S](t: Type): Mapping[[X] =>> State[S, X]] = new Mapping[[X] =>> State[S, X]]:
    def effect: Type = t
    def toKernel[X](op: State[S, X]): Op[?] = op match
      case State.Get() => new KState.Get[S](t)
      case State.Put(s) => new KState.Put[S](t, s)
    def answer[X](op: State[S, X], v: Any): X = op match
      case State.Get() => trust[X](v)
      case State.Put(_) => ()
    def fromKernel(op: Op[?]): Okayed[[X] =>> State[S, X], ?] =
      KState.narrow[S, Any](trust[Op[Any]](op)).fold(new KState.Cases[S, Any, Okayed[[X] =>> State[S, X], ?]]:
        def get(v: Same[S, Any]) = Okayed[[X] =>> State[S, X], S](State.Get[S](), s => s)
        def put(s: S, v: Same[Void, Any]) = Okayed[[X] =>> State[S, X], Unit](State.Put(s), _ => null))

  /** okay's `Reader % R` as the kernel's reader of the type `t`; `asks(f)` an ask, `f` applied here */
  def reader[R](t: Type): Mapping[[X] =>> Reader[R, X]] = new Mapping[[X] =>> Reader[R, X]]:
    def effect: Type = t
    def toKernel[X](op: Reader[R, X]): Op[?] = new KReader.Ask[R](t)
    def answer[X](op: Reader[R, X], v: Any): X = op match
      case Reader.Ask() => trust[X](v)
      case Reader.Asks(f) => f(trust[R](v))
    def fromKernel(op: Op[?]): Okayed[[X] =>> Reader[R, X], ?] = Okayed[[X] =>> Reader[R, X], R](Reader.Ask[R](), r => r)

  /** okay's `Writer % W` as the kernel's writer of the type `t` */
  def writer[W](t: Type): Mapping[[X] =>> Writer[W, X]] = new Mapping[[X] =>> Writer[W, X]]:
    def effect: Type = t
    def toKernel[X](op: Writer[W, X]): Op[?] = op match
      case Writer.Tell(w) => new KWriter.Tell[W](t, w)
    def answer[X](op: Writer[W, X], v: Any): X = op match
      case Writer.Tell(_) => ()
    def fromKernel(op: Op[?]): Okayed[[X] =>> Writer[W, X], ?] =
      KWriter.narrow[W, Any](trust[Op[Any]](op)).fold(new KWriter.Cases[W, Any, Okayed[[X] =>> Writer[W, X], ?]]:
        def tell(w: W, v: Same[Void, Any]) = Okayed[[X] =>> Writer[W, X], Unit](Writer.Tell(w), _ => null))

  /** okay's `Throws % E` as the kernel's of the type `t`: a raise never answered */
  def throws[E](t: Type): Mapping[[X] =>> Throws[E, X]] = new Mapping[[X] =>> Throws[E, X]]:
    def effect: Type = t
    def toKernel[X](op: Throws[E, X]): Op[?] = op match
      case Throws.Raise(e) => new KThrows.Raise[E, Any](t, e)
    def answer[X](op: Throws[E, X], v: Any): X = throw IllegalStateException(s"a raise answered: $op")
    def fromKernel(op: Op[?]): Okayed[[X] =>> Throws[E, X], ?] =
      KThrows.narrow[E, Any](trust[Op[Any]](op)).fold(new KThrows.Cases[E, Any, Okayed[[X] =>> Throws[E, X], ?]]:
        def raise(e: E) = Okayed[[X] =>> Throws[E, X], Nothing](Throws.Raise(e), n => n))

  /** okay's `Choose` as the kernel's, `Choose.TYPE` */
  val choose: Mapping[Choose] = new Mapping[Choose]:
    def effect: Type = KChoose.TYPE
    def toKernel[X](op: Choose[X]): Op[?] = op match
      case Choose.Among(as) => new KChoose.Among[Any](as.asJava)
    def answer[X](op: Choose[X], v: Any): X = trust[X](v)
    def fromKernel(op: Op[?]): Okayed[Choose, ?] =
      KChoose.narrow[Any](trust[Op[Any]](op)).fold(new KChoose.Cases[Any, Okayed[Choose, ?]]:
        def among(xs: java.util.List[Any]) = Okayed[Choose, Any](Choose.Among(xs.asScala.toSeq), x => x))

/**
 * A ROW OF OKAY'S SEEN BY THE KERNEL: each of its effects mapped (`Mapping`, found by the compiler for each) — the
 * capabilities of the row reaching the bridge (export), a kernel operation found in the row by its type (import)
 */
trait Kernel[R <: Row]:
  def types: List[Type]
  /** the row's capabilities at the border: each operation one capture to the nearest delimiter, the `Border` */
  def has(c: Border.Ctx): Has[R, c.type]
  /** the kernel's operation as one of the row's, with its path; `null`, none of the row's */
  def find(op: Op[?]): Found[R] | Null

/** a kernel operation found in a row: performed by okay's handler of it in force, its answer the kernel's */
trait Found[R <: Row]:
  def perform(c: Ctx)(has: Has[R, c.type]): Cont[c.Here, c.Here, Any]

object Kernel:
  given nil: Kernel[Pure] with
    def types: List[Type] = Nil
    def has(c: Border.Ctx): Has[Pure, c.type] = Has.HNil()
    def find(op: Op[?]): Found[Pure] | Null = null

  given cons[E[+_], T <: Row](using m: Mapping[E], t: Kernel[T]): Kernel[E +: T] with
    def types: List[Type] = m.effect :: t.types
    def has(c: Border.Ctx): Has[E +: T, c.type] = Has.HCons(Cap.Reaching(Border.reaches(m, c)), t.has(c))
    def find(op: Op[?]): Found[E +: T] | Null =
      if op.effect() eq m.effect then here(m.fromKernel(op))
      else t.find(op) match
        case null => null
        case f => there(f)
    private def here[X](o: Okayed[E, X]): Found[E +: T] = new Found[E +: T]:
      def perform(c: Ctx)(has: Has[E +: T, c.type]): Cont[c.Here, c.Here, Any] = has match
        case Has.HCons(cap, _) => cap.perform(o.op, c).map(o.back)
    private def there(f: Found[T]): Found[E +: T] = new Found[E +: T]:
      def perform(c: Ctx)(has: Has[E +: T, c.type]): Cont[c.Here, c.Here, Any] = has match
        case Has.HCons(_, rest) => f.perform(c)(rest)

/**
 * THE BORDER of an okay program placed in a kernel program: a delimiter of the bridge, transparent — the nearest one
 * to every operation of the program's row, which reaches it by okay's own static reach (`Reach.Here` from the
 * program's context, one level more for each of okay's handlers inside: okay crosses them). Its operation arrives as
 * the bridge's request, and goes on from here by the kernel's rules. An answer that is a signal unwinds from the
 * border: between it and the operation only okay's own frames and handlers, which have no finalizers
 */
object Border extends OnOkay.Delim:
  type Ctx = In[Any, Root.type]
  /** the context an okay program is run in, inside the border */
  val ctx: Ctx = In.at[Any](using Root)
  def done(v: Any): U = pureU(v)

  /** `E` reached from the program's context: the border, the clause making its operation the bridge's request */
  def reaches[E[+_]](m: Mapping[E], c: Ctx): Reaches[E, c.type] = new Reaches[E, c.type]:
    def target(c2: c.type): Target[E, c2.Here] =
      trust[Target[E, c2.Here]](Target(Reach.Here[EmptyTuple, Any](), clause(m)))

  private def clause[E[+_]](m: Mapping[E]): Clause[E, EmptyTuple, Any] = new Clause[E, EmptyTuple, Any]:
    def apply[X](op: E[X], k: X => U): U =
      val kop = try m.toKernel(op) catch case NonFatal(e) => return unwind(FailSig(e))
      Cont.Return(Perform(kop, v => answered(v)(a => try k(m.answer(op, a)) catch case NonFatal(e) => unwind(FailSig(e))), region = false))

/**
 * OKAY'S OWN PROGRAMS AND THE KERNEL'S: an okay program `A ! R` (`Free[R, A]`, its row mapped) as a kernel program
 * and computation, and a kernel computation as okay's program over a row
 */
object OkayPrograms:
  /** an okay program as a kernel program: on okay's bridge, under a border; on another implementation, imported */
  def lift[R <: Row, A](p: Free[R, A])(using k: Kernel[R]): Program[A] = e =>
    if e eq OnOkay then OnOkay.P[A](OnOkay.delayU(delimit(level[U](p.run(using Border.ctx, k.has(Border.ctx))), Border)))
    else e.foreign(exported(p))

  /** EXPORT of okay's program: a computation, its row the mapped types */
  def exported[R <: Row, A](p: Free[R, A])(using k: Kernel[R]): Computation[A] =
    OnOkay.computation(lift(p), new java.util.HashSet[Type](k.types.asJava))

  /** IMPORT into okay: a kernel computation as okay's program over `R` — each operation it hands out performed by
   * okay's handler of it in force, by its path in the row, its answer given back. okay has no failures nor
   * cancellation: a computation's failure is thrown out of okay's run; an operation of none of the row's effects is
   * failed into the computation (`k.fail`) */
  def imported[R <: Row, A](c: Computation[A])(using k: Kernel[R]): Free[R, A] = new Free[R, A]:
    def run(using ctx: Ctx, has: Has[R, ctx.type]): Cont[ctx.Here, ctx.Here, A] =
      def loop(s: Step[?]): Cont[ctx.Here, ctx.Here, A] = s match
        case d: Step.Done[?] => Cont.Return(trust[A](d.value()))
        case f: Step.Failed[?] => throw f.error()
        case _: Step.Cancelled[?] => throw java.util.concurrent.CancellationException("an imported computation cancelled")
        case y: Step.Yield[?, ?] =>
          val r = trust[Resume[Any, Any]](y.k())
          k.find(y.op()) match
            case null => Cont.Delay(() => loop(r.fail(IllegalStateException(s"no effect of the row for ${y.op()}"))))
            case f => f.perform(ctx)(has).flatMap(v => loop(r.resume(v)))
      Cont.Delay(() => loop(c.start(Answers.NONE)))
