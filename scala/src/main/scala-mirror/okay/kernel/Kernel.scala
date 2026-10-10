package okay.kernel

// THE API'S MIRROR for Scala.js and Scala Native, where there is no Java: the same names and signatures as the Java
// API (api/), so Scala written against it compiles alike on every platform. In the subset Scala 2.13 and 3 share;
// accessors with `()`, as Java's records have them

final class Type private (private val k: Any, private val as: Array[Type], private val i: Int) {
  def key(): Any = k
  def args(): Array[Type] = as.clone()
  def arity(): Int = as.length
  def arg(n: Int): Type = as(n)
  def id(): Int = i
  override def toString: String = {
    val name = k match {
      case null => "fresh#" + i
      case c: Class[?] => c.getSimpleName
      case v => v.toString
    }
    if (as.isEmpty) name else as.mkString(name + "[", ", ", "]")
  }
}
object Type {
  private final case class Interned(key: Any, args: List[Int])
  private val known = scala.collection.mutable.HashMap.empty[Interned, Type]
  private var next = 0
  def of(key: Any, args: Type*): Type = synchronized {
    if (key == null) throw new IllegalArgumentException("a type's key is not null")
    val k = key match { case c: Class[?] => boxed(c); case v => v }
    known.getOrElseUpdate(Interned(k, args.toList.map(_.id())), { next += 1; new Type(k, args.toArray, next - 1) })
  }
  def fresh(): Type = synchronized { next += 1; new Type(null, Array.empty[Type], next - 1) }
  private def boxed(c: Class[?]): Any =
    if (c == classOf[Int]) classOf[java.lang.Integer]
    else if (c == classOf[Long]) classOf[java.lang.Long]
    else if (c == classOf[Boolean]) classOf[java.lang.Boolean]
    else if (c == classOf[Double]) classOf[java.lang.Double]
    else if (c == classOf[Float]) classOf[java.lang.Float]
    else if (c == classOf[Short]) classOf[java.lang.Short]
    else if (c == classOf[Byte]) classOf[java.lang.Byte]
    else if (c == classOf[Char]) classOf[java.lang.Character]
    else if (c == classOf[Unit]) classOf[java.lang.Void]
    else c
}

trait Op[X] {
  def effect(): Type
  def value(): Type = null
}

trait Fn[A, B] { def apply(a: A): B }
trait Thunk[A] { def get(): A }

sealed trait Same[A, B] { def apply(a: A): B }
object Same {
  final case class Refl[A]() extends Same[A, A] { def apply(a: A): A = a }
}

sealed trait Step[A]
object Step {
  final case class Done[A](private val v: A) extends Step[A] { def value(): A = v }
  final case class Yield[X, A](private val o: Op[X], private val r: Resume[X, A]) extends Step[A] { def op(): Op[X] = o; def k(): Resume[X, A] = r }
  final case class Failed[A](private val e: Throwable) extends Step[A] { def error(): Throwable = e }
  final case class Cancelled[A]() extends Step[A]
}

trait Resume[X, A] {
  def resume(x: X): Step[A]
  def fail(e: Throwable): Step[A]
  def cancel(): Step[A]
}

trait Answers {
  def answers(effect: Type): Boolean
  def value[X](op: Op[X]): X
}
object Answers {
  val NONE: Answers = new Answers {
    def answers(effect: Type): Boolean = false
    def value[X](op: Op[X]): X = throw new IllegalStateException("not answered in place: " + op)
  }
}

trait Computation[A] {
  def start(host: Answers): Step[A]
  def effects(): java.util.Set[Type]
  def value(): Type = null
}

trait Prog[A]

trait Program[A] { def in(e: Effects): Prog[A] }

trait Effects {
  def pure[A](a: A): Prog[A]
  def flatMap[A, B](p: Prog[A], f: Fn[A, Prog[B]]): Prog[B]
  def map[A, B](p: Prog[A], f: Fn[A, B]): Prog[B] = flatMap(p, new Fn[A, Prog[B]] { def apply(a: A): Prog[B] = pure(f(a)) })
  def delay[A](p: Thunk[Prog[A]]): Prog[A]
  def perform[X](op: Op[X]): Prog[X]
  def handle[A, R](h: Handler[A, R], body: Program[A]): Prog[R]
  def shift0[A, I, O](body: Fn[Fn[A, Prog[I]], Prog[O]]): Prog[A]
  def dollar[A, I, O](body: Program[A], k: Fn[A, Prog[I]]): Prog[O]
  def failed[A](e: Throwable): Prog[A]
  def recover[A](body: Program[A], h: Fn[Throwable, Prog[A]]): Prog[A]
  def ensuring[A](body: Program[A], finalizer: Program[Void]): Prog[A]
  def uncancelable[A](body: Program[A]): Prog[A]
  def computation[A](program: Program[A], effects: java.util.Set[Type]): Computation[A]
  def foreign[A](computation: Computation[A]): Prog[A]
}

trait Handler[A, R] { def effect(): Type }
trait Answering[A, R] extends Handler[A, R] { def value[X](op: Op[X]): X }
trait Aborting[A, R] extends Handler[A, R] { def abort[X](op: Op[X]): R }
trait Returning[A, R] extends Handler[A, R] { def answer(a: A): R }
trait General[A, R] extends Handler[A, R] {
  def ret(a: A, e: Effects): Prog[R]
  def apply[X](op: Op[X], k: Fn[X, Prog[R]], e: Effects): Prog[R]
}

trait Direct {
  def directPure(a: Any): Any
  def directFlatMap(p: Any, f: Fn[Any, Any]): Any
  def directMap(p: Any, f: Fn[Any, Any]): Any
  def directDelay(p: Thunk[Any]): Any
  def directPerform(op: Op[?]): Any
  def directHandle(h: Handler[?, ?], body: Program[?]): Any
  def prog[A](p: Any): Prog[A]
  def unprog(p: Prog[?]): Any
}
