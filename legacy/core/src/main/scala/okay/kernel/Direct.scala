package okay.kernel

/**
 * A MONAD THAT BUILDS SCALA'S TYPED PROGRAMS DIRECTLY — optional, beside `Monad`: Scala 3's `Eff` built in it node by
 * node in the monad's own programs, `Any` here, with no kernel `Prog` around each and no function translating each
 * bind — the program's function the monad's own. `prog` and `unprog` cross to the kernel's programs where they meet
 * them: a handler's clause, its `k`, a program of another language. Scala's alone (a monad of the kernel written in
 * Scala over a library: `OnCore`); Java's programs, and every handler, go by `Monad` as before
 */
trait Direct extends Monad:
  def directPure(a: Any): Any
  def directFlatMap(p: Any, f: Any => Any): Any
  def directMap(p: Any, f: Any => Any): Any
  def directDelay(p: () => Any): Any
  def directPerform(op: Op[?]): Any
  def directHandle(h: Handler[?, ?], body: Program[?]): Any
  /** the monad's program as the kernel's */
  def prog[A](p: Any): Prog[A]
  /** the kernel's program, of this monad, as the monad's own */
  def unprog(p: Prog[?]): Any
