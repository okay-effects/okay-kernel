//> using scala 2.13.18
//> using options -Xfatal-warnings -Xlint

/** X and Y the same: its only proof Refl, apply the identity — no cast */
sealed trait Same[A, B] { def apply(a: A): B }
final case class Refl[A]() extends Same[A, A] { def apply(a: A): A = a }

trait Op[+X]

/** an effect in the shared subset: a sealed trait, its cases, and the cases as a visitor with the proofs */
sealed trait State[S, X] extends Op[X] {
  def fold[R](c: State.Cases[S, X, R]): R
}
object State {
  trait Cases[S, X, R] {
    def get(value: Same[S, X]): R
    def put(s: S, value: Same[Unit, X]): R
  }
  final case class Get[S]() extends State[S, S] { def fold[R](c: Cases[S, S, R]): R = c.get(Refl()) }
  final case class Put[S](s: S) extends State[S, Unit] { def fold[R](c: Cases[S, Unit, R]): R = c.put(s, Refl()) }
}

object Exh2 {
  var s: Int = 1
  /** a clause: total — a case missing does not compile — and typed, the value through the proof */
  def clause[X, R](op: State[Int, X], k: X => R): R = op.fold(new State.Cases[Int, X, R] {
    def get(v: Same[Int, X]): R = k(v(s))
    def put(n: Int, v: Same[Unit, X]): R = { s = n; k(v(())) }
  })
  def main(args: Array[String]): Unit = {
    println(clause(State.Get[Int](), (x: Int) => s"got $x"))
    println(clause(State.Put(5), (_: Unit) => "put"))
    println(clause(State.Get[Int](), (x: Int) => s"got $x"))
  }
}
