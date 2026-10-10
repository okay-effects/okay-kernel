package okay.kernel

import okay.{!, %, +, Eff}
import okay.std.*

/** programs of Java and Scala 3 in one, both ways, on every monad */
abstract class TestFromJava(m: Monad) extends munit.FunSuite:
  test("a program written in Java, under Scala 3's typed handlers") {
    val q: Int ! Writer % String + Async = Eff.from(FromJava.q(2).asInstanceOf[Program[Int]])
    assertEquals(Eff.run(writer(async(q)), m), (List("java 2"), 21))
  }
  test("Scala 3's program, its handlers installed from Java") {
    assertEquals(FromJava.scalasUnderJavas(m), (List("got 1", "awaited 2"), 4))
  }

  test("Scala 3's program under the handlers for Java: the log a java.util.List") {
    val r = FromJava.scalasUnderJavaTypes(m)
    assertEquals(r.log, java.util.List.of("got 1", "awaited 2"))
    assertEquals(r.value: Any, 4: Any)
  }

class TestFromJavaSteps extends TestFromJava(Steps)
class TestFromJavaRef extends TestFromJava(Ref)
