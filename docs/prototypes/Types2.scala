//> using scala 2.13.18
//> using options -Xfatal-warnings
import scala.reflect.ClassTag

final class Type private (val con: Class[_], val args: List[Type], val id: Int) {
  override def toString: String = if (args.isEmpty) con.getSimpleName else s"${con.getSimpleName}[${args.mkString(", ")}]"
}
object Type {
  private val known = scala.collection.mutable.HashMap.empty[(Class[_], List[Int]), Type]
  /** a primitive is its box: Scala's Int and Java's Integer one type */
  private def boxed(c: Class[_]): Class[_] = if (!c.isPrimitive) c else c match {
    case java.lang.Integer.TYPE => classOf[java.lang.Integer]
    case java.lang.Long.TYPE => classOf[java.lang.Long]
    case java.lang.Boolean.TYPE => classOf[java.lang.Boolean]
    case java.lang.Double.TYPE => classOf[java.lang.Double]
    case _ => c
  }
  def of(con: Class[_], args: Type*): Type = synchronized {
    val c = boxed(con)
    known.getOrElseUpdate((c, args.toList.map(_.id)), new Type(c, args.toList, known.size))
  }
}

trait TypeOf[T] { def get: Type }
object TypeOf extends TypeOf1 {
  implicit def con2[F[_, _], A, B](implicit c: ClassTag[F[Any, Any]], a: TypeOf[A], b: TypeOf[B]): TypeOf[F[A, B]] =
    new TypeOf[F[A, B]] { val get = Type.of(c.runtimeClass, a.get, b.get) }
}
trait TypeOf1 extends TypeOf0 {
  implicit def con1[F[_], A](implicit c: ClassTag[F[Any]], a: TypeOf[A]): TypeOf[F[A]] =
    new TypeOf[F[A]] { val get = Type.of(c.runtimeClass, a.get) }
}
trait TypeOf0 {
  implicit def con0[A](implicit c: ClassTag[A]): TypeOf[A] = new TypeOf[A] { val get = Type.of(c.runtimeClass) }
}

final class State[S]

object Ty2 {
  def typeOf[T](implicit t: TypeOf[T]): Type = t.get
  def main(args: Array[String]): Unit = {
    val a = typeOf[State[List[Int]]]
    println(s"$a same: ${a eq typeOf[State[List[Int]]]}, differs from String: ${!(a eq typeOf[State[List[String]]])}")
    println(typeOf[State[Map[String, Option[Int]]]])
    println(s"Int is Integer: ${typeOf[Int] eq Type.of(classOf[java.lang.Integer])}")
  }
}
