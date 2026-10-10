//> using scala 3.9.0
//> using options -Werror
import scala.reflect.ClassTag
import java.util.concurrent.ConcurrentHashMap

/** AN EFFECT'S IDENTITY: a key applied to arguments — the key a class or ANY value (a string, an object, an enum's
 * case), compared by equals; interned, so `eq` is equality. Or fresh: no other is it */
final class Type private (val key: Any, val args: List[Type], val id: Int):
  override def toString: String =
    val k = key match { case c: Class[?] => c.getSimpleName; case null => s"fresh#$id"; case v => v.toString }
    if args.isEmpty then k else s"$k[${args.mkString(", ")}]"
object Type:
  private val known = ConcurrentHashMap[(Any, List[Int]), Type]()
  private val next = java.util.concurrent.atomic.AtomicInteger()
  def of(key: Any, args: Type*): Type =
    val k = key match { case c: Class[?] => boxed(c); case v => v }
    known.computeIfAbsent((k, args.toList.map(_.id)), _ => Type(k, args.toList, next.getAndIncrement()))
  def fresh(): Type = Type(null, Nil, next.getAndIncrement())
  private def boxed(c: Class[?]): Class[?] = if !c.isPrimitive then c else c match
    case java.lang.Integer.TYPE => classOf[java.lang.Integer]
    case java.lang.Long.TYPE => classOf[java.lang.Long]
    case _ => c

/** a tag on a type, as libraries write it: `Long @@ UserId` — opaque outside this object */
object tags:
  opaque type @@[+A, T] = A
import tags.@@

/** the exact type of T, found by the compiler: a class, a class applied, a tagged type — its tag's key an object, a
 * literal or a class; for anything else one line by hand, `TypeOf.named[T](key)` */
trait TypeOf[T] { def get: Type }
object TypeOf extends TypeOf2:
  def named[T](key: Any, args: Type*): TypeOf[T] = new TypeOf[T] { val get = Type.of(key, args*) }
  given tagged[A, T](using a: TypeOf[A], t: TagKey[T]): TypeOf[A @@ T] = named(t.key, a.get)
trait TypeOf2 extends TypeOf1:
  given con2[F[_, _], A, B](using c: ClassTag[F[Any, Any]], a: TypeOf[A], b: TypeOf[B]): TypeOf[F[A, B]] = TypeOf.named(c.runtimeClass, a.get, b.get)
trait TypeOf1 extends TypeOf0:
  given con1[F[_], A](using c: ClassTag[F[Any]], a: TypeOf[A]): TypeOf[F[A]] = TypeOf.named(c.runtimeClass, a.get)
trait TypeOf0:
  given con0[A](using c: ClassTag[A]): TypeOf[A] = TypeOf.named(c.runtimeClass)

/** a tag's key: a singleton (an object, a literal) its value, otherwise its class */
trait TagKey[T] { def key: Any }
object TagKey extends TagKey0:
  given single[T](using v: ValueOf[T]): TagKey[T] = new TagKey[T] { val key = v.value }
trait TagKey0:
  given byClass[T](using c: ClassTag[T]): TagKey[T] = new TagKey[T] { val key = c.runtimeClass }

def typeOf[T](using t: TypeOf[T]): Type = t.get

final class State[S]
object UserId                 // a tag, an object
trait OrderTag                // a tag, a trait
type User = Long @@ UserId.type
type Order = Long @@ OrderTag
type Mail = String @@ "mail"  // a tag, a literal
object skus:
  opaque type Sku = String
  given TypeOf[Sku] = TypeOf.named("Sku")   // anything else: one line
import skus.{Sku, given}

@main def key(): Unit =
  println(typeOf[State[User]])
  println(typeOf[State[Order]])
  println(typeOf[State[Mail]])
  println(typeOf[State[Sku]])
  println(s"same: ${typeOf[State[User]] eq typeOf[State[Long @@ UserId.type]]}, differ: ${!(typeOf[User] eq typeOf[Order])}")
