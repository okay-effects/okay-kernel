//> using scala 3.9.0
//> using options -Werror

/** the kernel's: an operation of value X, covariant */
trait Op[+X]
/** the kernel's: a handler of the effect E (first-order: a type of kind *), its clause typed by the operation */
trait Handler[E, R]:
  def apply[X](op: E & Op[X], k: X => R): R

/** an effect, a GADT enum as always; the effect in a row is State[S, Any] — first-order */
enum State[S, +X] extends Op[X]:
  case Get[S]() extends State[S, S]
  case Put[S](s: S) extends State[S, Unit]

def cell[S, R](var0: S): Handler[State[S, Any], R] = new Handler[State[S, Any], R]:
  var s = var0
  def apply[X](op: State[S, Any] & Op[X], k: X => R): R = op match
    case State.Get() => k(s)          // X refined: S <: X
    case State.Put(v) => s = v; k(())  // Unit <: X

@main def exhMain(): Unit =
  val h = cell[Int, String](1)
  println(h(State.Get[Int](), (x: Int) => s"got $x"))
  println(h(State.Put(5), (_: Unit) => "put"))
  println(h(State.Get[Int](), (x: Int) => s"got $x"))
