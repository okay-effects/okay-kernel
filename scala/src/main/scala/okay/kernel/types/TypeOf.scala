package okay.kernel.types

import scala.reflect.ClassTag
import okay.kernel.Type

/**
 * THE EXACT TYPE OF `T`, FOUND BY THE COMPILER, no macro: a class (its `ClassTag`), a class applied (by its
 * constructor's arity, up to three), a tagged type `A @@ T` (its tag's key: a singleton's value — an object, a literal
 * — or a class). Anything else in one line, by hand: `implicit val sku: TypeOf[Sku] = TypeOf.named("Sku")`.
 * In the subset Scala 2.13 and 3 share: implicits
 */
trait TypeOf[T] { def get: Type }

object TypeOf extends TypeOf3 {
  def apply[T](implicit t: TypeOf[T]): TypeOf[T] = t
  def of[T](t: Type): TypeOf[T] = new TypeOf[T] { val get: Type = t }
  /** a type by its key, applied */
  def named[T](key: Any, args: Type*): TypeOf[T] = of[T](Type.of(key, args*))

  implicit def tagged[A, T](implicit a: TypeOf[A], t: TagKey[T]): TypeOf[A @@ T] = named[A @@ T](t.key, a.get)
}
private[types] trait TypeOf3 extends TypeOf2 {
  implicit def con3[F[_, _, _], A, B, C](implicit c: ClassTag[F[Any, Any, Any]], a: TypeOf[A], b: TypeOf[B], d: TypeOf[C]): TypeOf[F[A, B, C]] =
    TypeOf.named[F[A, B, C]](c.runtimeClass, a.get, b.get, d.get)
}
private[types] trait TypeOf2 extends TypeOf1 {
  implicit def con2[F[_, _], A, B](implicit c: ClassTag[F[Any, Any]], a: TypeOf[A], b: TypeOf[B]): TypeOf[F[A, B]] =
    TypeOf.named[F[A, B]](c.runtimeClass, a.get, b.get)
}
private[types] trait TypeOf1 extends TypeOf0 {
  implicit def con1[F[_], A](implicit c: ClassTag[F[Any]], a: TypeOf[A]): TypeOf[F[A]] =
    TypeOf.named[F[A]](c.runtimeClass, a.get)
}
private[types] trait TypeOf0 {
  implicit def con0[A](implicit c: ClassTag[A]): TypeOf[A] = TypeOf.named[A](c.runtimeClass)
}

/** a tag's key: a singleton's value (an object, a literal), else its class */
trait TagKey[T] { def key: Any }
object TagKey extends TagKey0 {
  implicit def single[T](implicit v: ValueOf[T]): TagKey[T] = new TagKey[T] { val key: Any = v.value }
}
private[types] trait TagKey0 {
  implicit def byClass[T](implicit c: ClassTag[T]): TagKey[T] = new TagKey[T] { val key: Any = c.runtimeClass }
}
