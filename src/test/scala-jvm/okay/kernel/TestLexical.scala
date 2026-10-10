package okay.kernel

import okay.lexical.*
import okay.kernel.std.{Final, Result}

/** the kernel by levels: Java's programs, statically checked, each capability its own handler's — on every monad */
abstract class TestLexical(m: Monad) extends munit.FunSuite:
  test("two logs of one type nested: each capability its own handler's, past the other") {
    val r = Prog.run(LexicalFromJava.twoLogs(), m)
    assertEquals(r.log, java.util.List.of("to the outer"))
    assertEquals(r.value.log, java.util.List.of("to the inner"))
    assertEquals(r.value.value: Any, 7: Any)
  }
  test("a state counted, a failure raised past it") {
    assertEquals(Prog.run(LexicalFromJava.counted(5), m): Any, Result.ok(Final(5, 5)): Any)
    assertEquals(Prog.run(LexicalFromJava.counted(20), m): Any, Result.failed("over 20"): Any)
  }
  test("Scala 3's program under Java's handlers, by type") {
    assertEquals(LexicalFromJava.scalaUnderJava(m), (List("got 3", "awaited 4"), 8))
  }

class TestLexicalSteps extends TestLexical(Steps)
class TestLexicalRef extends TestLexical(Ref)

/** the levels' guarantee: a capability does not leave its handler's body — Java's compiler says so */
class TestLexicalLeaks extends munit.FunSuite:
  val writing = """return Prog.writer(new WriterBody<Top, String, Integer>() {
    |  public <M extends Top> Prog<M, Integer> in(Writer<M, String> w) { %s }
    |});""".stripMargin

  test("used inside its body: compiles") {
    assertEquals(Javac.errors(writing.format("return w.tell(\"x\").andThen(Prog.pure(1));")), Nil)
  }
  test("the capability kept outside its body: does not compile — its level is the body's") {
    val e = Javac.errors(writing.format("Writer<Top, String> out = w; return Prog.pure(1);"))
    assert(e.exists(_.contains("incompatible types")), e.toString)
  }
  test("a program of the capability at the top's level: does not compile") {
    val e = Javac.errors(writing.format("Prog<Top, Integer> p = w.tell(\"x\").map(u -> 1); return Prog.pure(1);"))
    assert(e.exists(_.contains("incompatible types")), e.toString)
  }

/** the answer type, moved by a shift, checked by Java's compiler */
class TestContTypes extends munit.FunSuite:
  // Scala's Cont.Flat[I, O, A], in Java its whole type: Cont<Cont<I, I, I>, Cont<O, O, O>, A>
  val moving = """okay.Cont<okay.Cont<Integer, Integer, Integer>, okay.Cont<String, String, String>, Integer> b =
    |  okay.Cont.<Integer, String, Integer>shift(k -> okay.Cont.pure("x"));
    |%s""".stripMargin
  test("the reset's answer what the shift moved it to, a String: compiles") {
    assertEquals(Javac.errors(moving.format(
      "okay.Cont<okay.Cont<Object, Object, Object>, okay.Cont<Object, Object, Object>, String> r = okay.Cont.reset(b); return r;")), Nil)
  }
  test("taken as an Integer, the answer before the move: does not compile") {
    val e = Javac.errors(moving.format(
      "okay.Cont<okay.Cont<Object, Object, Object>, okay.Cont<Object, Object, Object>, Integer> r = okay.Cont.reset(b); return r;"))
    assert(e.nonEmpty, "compiled")
  }
