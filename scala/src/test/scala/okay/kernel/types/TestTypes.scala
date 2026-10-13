package okay.kernel.types

import munit.FunSuite
import okay.kernel.{Fn, Same, Type}
import okay.kernel.std.{Choose, State, Throws}

final class Box[A]
object UserId
trait OrderTag

/** the API from Scala, alike on every platform and in Scala 2.13 and 3: exact types found by the compiler; the
 * vocabulary's cases total and typed, no cast */
class TestTypes extends FunSuite {
  test("exact types: classes, applied, tagged; one object for one type") {
    assert(typeOf[Box[List[Int]]] eq typeOf[Box[List[Int]]])
    assert(!(typeOf[Box[List[Int]]] eq typeOf[Box[List[String]]]))
    assert(typeOf[Int] eq Type.of(classOf[java.lang.Integer]), "Int is Integer")
    assert(typeOf[Map[String, Option[Int]]] eq typeOf[Map[String, Option[Int]]])
    assert(!(typeOf[Long @@ UserId.type] eq typeOf[Long @@ OrderTag]))
    assert(typeOf[Long @@ UserId.type] eq Type.of(UserId, typeOf[Long]))
    assert(typeOf[String @@ "mail"] eq Type.of("mail", typeOf[String]))
    implicit val sku: TypeOf[Sku] = TypeOf.named[Sku]("com.acme.Sku")
    assert(typeOf[Box[Sku]] eq Type.of(classOf[Box[?]], Type.of("com.acme.Sku")))
  }

  var cell = 1
  def clause[X, R](op: State.Ops[Int, X], k: X => R): R = op.fold(new State.Cases[Int, X, R] {
    def get(value: Same[Int, X]): R = k(value(cell))
    def put(s: Int, value: Same[Void, X]): R = { cell = s; k(value(null)) }
  })

  test("a handler's clause in the portable form: total and typed, no cast") {
    val st = State.key[Int](typeOf[Int])
    assertEquals(clause(new State.Get[Int](st), (x: Int) => s"got $x"), "got 1")
    assertEquals(clause(new State.Put[Int](st, 5), (_: Void) => "put"), "put")
    assertEquals(clause(new State.Get[Int](st), (x: Int) => s"got $x"), "got 5")
    val raised = new Throws.Raise[String, Int](Throws.key[String](typeOf[String]), "boom")
    assertEquals(raised.fold(new Throws.Cases[String, Int, String] { def raise(e: String): String = s"caught $e" }), "caught boom")
    assert(new Choose.Among[Int](java.util.Arrays.asList(1, 2)).effect() eq Choose.TYPE)
  }
}

/** a type the compiler cannot name: given by hand */
final class Sku
