//> using scala 3.9.0
//> using options -Werror
import scala.reflect.ClassTag
import java.util.concurrent.ConcurrentHashMap

/** A TYPE CONSTRUCTOR: a class, or one the user declares — an identity by object, its arity */
final class Con private (val name: String, val cls: Class[?] | Null, val arity: Int):
  override def toString: String = name
object Con:
  private val byClass = ConcurrentHashMap[Class[?], Con]()
  /** a class's constructor: one for all who ask */
  def of(c: Class[?]): Con = byClass.computeIfAbsent(boxed(c), k => Con(k.getSimpleName, k, k.getTypeParameters.length))
  /** a constructor of one's own: a constant, declared once, `name` for reading only */
  def declare(name: String, arity: Int): Con = Con(name, null, arity)
  private def boxed(c: Class[?]): Class[?] = if !c.isPrimitive then c else c match
    case java.lang.Integer.TYPE => classOf[java.lang.Integer]
    case java.lang.Long.TYPE => classOf[java.lang.Long]
    case java.lang.Boolean.TYPE => classOf[java.lang.Boolean]
    case java.lang.Double.TYPE => classOf[java.lang.Double]
    case _ => c

/** AN EXACT TYPE: a constructor applied, a union or an intersection (sets: order and repeats say nothing), or fresh —
 * interned, so `eq` is equality; `id` dense, for the machines' arrays */
final class Type private (val con: Con | Null, val kind: Int, val args: List[Type], val id: Int):
  override def toString: String = kind match
    case Type.Applied => if args.isEmpty then con.toString else s"$con[${args.mkString(", ")}]"
    case Type.Union => args.mkString("(", " | ", ")")
    case Type.Inter => args.mkString("(", " & ", ")")
    case _ => s"fresh#$id"
object Type:
  private inline val Applied = 0
  private inline val Union = 1
  private inline val Inter = 2
  private inline val Fresh = 3
  private val known = ConcurrentHashMap[(AnyRef, Int, List[Int]), Type]()
  private val next = java.util.concurrent.atomic.AtomicInteger()
  private def intern(con: Con | Null, kind: Int, args: List[Type]): Type =
    known.computeIfAbsent((if con == null then "" else con, kind, args.map(_.id)), _ => Type(con, kind, args, next.getAndIncrement()))
  def of(c: Con, args: Type*): Type =
    require(args.length == c.arity, s"$c takes ${c.arity} arguments, given ${args.length}")
    intern(c, Applied, args.toList)
  def of(c: Class[?], args: Type*): Type = of(Con.of(c), args*)
  /** a union, a set: flattened, sorted, without repeats */
  def union(ts: Type*): Type = set(Union, ts)
  def inter(ts: Type*): Type = set(Inter, ts)
  private def set(kind: Int, ts: Seq[Type]): Type =
    val flat = ts.flatMap(t => if t.kind == kind then t.args else List(t)).distinct.sortBy(_.id).toList
    if flat.length == 1 then flat.head else intern(null, kind, flat)
  /** a type no other is: generative — a capability's effect, one a run of its handler */
  def fresh(): Type = Type(null, Fresh, Nil, next.getAndIncrement())

/** the exact type of T: found by the compiler for classes and classes applied; given by hand for any other */
trait TypeOf[T] { def get: Type }
object TypeOf extends TypeOf1:
  def apply[T](t: Type): TypeOf[T] = new TypeOf[T] { val get = t }
  given con2[F[_, _], A, B](using c: ClassTag[F[Any, Any]], a: TypeOf[A], b: TypeOf[B]): TypeOf[F[A, B]] = TypeOf(Type.of(c.runtimeClass, a.get, b.get))
trait TypeOf1 extends TypeOf0:
  given con1[F[_], A](using c: ClassTag[F[Any]], a: TypeOf[A]): TypeOf[F[A]] = TypeOf(Type.of(c.runtimeClass, a.get))
trait TypeOf0:
  given con0[A](using c: ClassTag[A]): TypeOf[A] = TypeOf(Type.of(c.runtimeClass))
def typeOf[T](using t: TypeOf[T]): Type = t.get

// -- what the user writes for types the compiler cannot name
final class State[S]
object ids:
  opaque type UserId = Long
  /** an opaque type: a constructor of one's own */
  val UserIdCon: Con = Con.declare("UserId", 0)
  given TypeOf[UserId] = TypeOf(Type.of(UserIdCon))
import ids.{UserId, given}
/** a union: by hand, from its parts */
given TypeOf[Int | String] = TypeOf(Type.union(typeOf[Int], typeOf[String]))
/** a label: two State[Int] told apart */
val Labeled: Con = Con.declare("Labeled", 2)
final class Counter; final class Total

@main def alg(): Unit =
  println(typeOf[State[UserId]])
  println(typeOf[State[Int | String]] eq Type.of(classOf[State[?]], Type.union(typeOf[String], typeOf[Int], typeOf[Int])))
  val counter = Type.of(Labeled, typeOf[State[Int]], typeOf[Counter])
  val total = Type.of(Labeled, typeOf[State[Int]], typeOf[Total])
  println(s"$counter ≠ $total: ${!(counter eq total)}")
  val f1 = Type.fresh(); val f2 = Type.fresh()
  println(s"fresh differ: ${!(f1 eq f2)}")
  println(scala.util.Try(Type.of(classOf[State[?]])).failed.map(_.getMessage).getOrElse("accepted"))
