package okay.kernel.js

import scala.scalajs.js
import scala.scalajs.js.annotation._

import okay.kernel._
import okay.kernel.std._
import okay.kernel.steps.Steps
import okay.kernel.types.typeOf

// THE PROTOCOL IN JAVASCRIPT: a computation of any implementation as a plain JS object, so that a JS implementation —
// okay-ts — and the kernel's (okay-core's on Scala.js, the reference machine) run each other's programs:
//
//   computation  { start(host): step, effects(): string[] }
//   step         { tag: "done", value } | { tag: "yield", op, k } | { tag: "failed", error } | { tag: "cancelled" }
//   k            { resume(x): step, fail(e): step, cancel(): step }        any number of times
//   host         { answers(effect: string): boolean, value(op): any }      answered in place
//   op           { effect: string, name: string, args: any[] }
//
// An effect by its type's name: `State[Integer]`, `Reader[String]`, `Choose` — the vocabulary's — or a name of one's
// own (`Sku`: a type keyed by that string, okay-kernel's last resort). The vocabulary's operations by their cases'
// names (`get`, `put`, `ask`, `tell`, `raise`, `among`, `send`); `among` given the number of the values, answered an
// index (okay-ts's), the kernel's `Among(xs)` answered that element. `Void` is `undefined` in JS.

/** an operation of an effect not of the vocabulary, as JS has it */
final class JsOp(val tpe: Type, val name: String, val args: js.Array[Any]) extends Op[Any] {
  private val k: Key[Any] = Key.of[Any](tpe)
  def key(): Key[?] = k
  override def toString: String = name + args.mkString("(", ", ", ")")
}

object KernelJs {
  private val Family = """(\w+)\[(\w+)\]""".r

  /** an argument of the vocabulary's families: the compiler's own types for the primitives (`typeOf`), else a name */
  def primitive(name: String): Type = name match {
    case "Integer" => typeOf[java.lang.Integer]
    case "String"  => typeOf[String]
    case "Boolean" => typeOf[java.lang.Boolean]
    case "Double"  => typeOf[java.lang.Double]
    case other     => Type.of(other)
  }

  /** a type by its name */
  def typeNamed(name: String): Type = name match {
    case "Choose"                => Choose.TYPE
    case "Async"                 => Async.TYPE
    case Family("State", a)      => State.of(primitive(a))
    case Family("Reader", a)     => Reader.of(primitive(a))
    case Family("Writer", a)     => Writer.of(primitive(a))
    case Family("Throws", a)     => Throws.of(primitive(a))
    case Family("Emit", a)       => Emit.of(primitive(a))
    case other                   => Type.of(other)
  }

  def nameOf(t: Type): String = t.toString

  private def lit(effect: String, name: String, args: Any*): js.Dynamic =
    js.Dynamic.literal(effect = effect, name = name, args = js.Array(args*))

  private val void: Any => Any = _ => null
  private val same: Any => Any = x => x
  private val undef: Any => Any = _ => js.undefined

  /** a kernel's operation as JS has it, and the way its answer comes back */
  def opToJs(op: Op[?]): (js.Dynamic, Any => Any) = {
    val e = nameOf(op.effect())
    op match {
      case _: State.Get[?]       => (lit(e, "get"), same)
      case p: State.Put[?]       => (lit(e, "put", p.s()), void)
      case _: Reader.Ask[?]      => (lit(e, "ask"), same)
      case t: Writer.Tell[?]     => (lit(e, "tell", t.w()), void)
      case r: Throws.Raise[?, ?] => (lit(e, "raise", r.error()), same)
      case s: Emit.Send[?]       => (lit(e, "send", s.w()), void)
      case a: Choose.Among[?]    => (lit(e, "among", a.xs().size()), i => a.xs().get(i.asInstanceOf[Int]))
      case j: JsOp               => (js.Dynamic.literal(effect = e, name = j.name, args = j.args), same)
      case other                 => (lit(e, other.getClass.getSimpleName), same)
    }
  }

  /** a JS operation as the kernel has it, and the way the kernel's answer goes back */
  def opFromJs(o: js.Dynamic): (Op[Any], Any => Any) = {
    val t = typeNamed(o.effect.asInstanceOf[String])
    val name = o.name.asInstanceOf[String]
    val args = o.args.asInstanceOf[js.Array[Any]]
    def arg = args(0)
    def op[X](x: Op[X]): Op[Any] = x.asInstanceOf[Op[Any]]
    (t.toString.takeWhile(_ != '['), name) match {
      case ("State", "get")   => (op(State.Get[Any](Key.of[State[Any]](t))), same)
      case ("State", "put")   => (op(State.Put[Any](Key.of[State[Any]](t), arg)), undef)
      case ("Reader", "ask")  => (op(Reader.Ask[Any](Key.of[Reader[Any]](t))), same)
      case ("Writer", "tell") => (op(Writer.Tell[Any](Key.of[Writer[Any]](t), arg)), undef)
      case ("Throws", "raise") => (op(Throws.Raise[Any, Any](Key.of[Throws[Any]](t), arg)), same)
      case ("Emit", "send")   => (op(Emit.Send[Any](Key.of[Emit[Any]](t), arg)), undef)
      case ("Choose", "among") =>
        val xs = new java.util.ArrayList[Any]()
        for (i <- 0 until arg.asInstanceOf[Int]) xs.add(i)
        (op(Choose.Among[Any](Choose.KEY, xs)), same)
      case _ => (new JsOp(t, name, args), same)
    }
  }

  private def throwable(e: Any): Throwable = e match {
    case t: Throwable => t
    case other        => js.JavaScriptException(other)
  }
  private def jsError(t: Throwable): Any = t match {
    case js.JavaScriptException(e) => e
    case other                     => other
  }

  // EXPORT: a kernel's computation to JS

  def stepToJs(s: Step[?]): js.Dynamic = s match {
    case d: Step.Done[?] => js.Dynamic.literal(tag = "done", value = d.value().asInstanceOf[js.Any])
    case y: Step.Yield[?, ?] =>
      val (op, back) = opToJs(y.op())
      val k = y.k().asInstanceOf[Resume[Any, Any]]
      js.Dynamic.literal(
        tag = "yield",
        op = op,
        k = js.Dynamic.literal(
          resume = (x: Any) => stepToJs(k.resume(back(x))),
          fail = (e: Any) => stepToJs(k.fail(throwable(e))),
          cancel = () => stepToJs(k.cancel())))
    case f: Step.Failed[?] => js.Dynamic.literal(tag = "failed", error = jsError(f.error()).asInstanceOf[js.Any])
    case _                 => js.Dynamic.literal(tag = "cancelled")
  }

  def hostFromJs(host: js.Any): Answers =
    if (js.isUndefined(host) || host == null) Answers.NONE
    else {
      val h = host.asInstanceOf[js.Dynamic]
      new Answers {
        def answers(effect: Type): Boolean = h.answers(nameOf(effect)).asInstanceOf[Boolean]
        def value[X](op: Op[X]): X = {
          val (o, back) = opToJs(op)
          back(h.value(o)).asInstanceOf[X]
        }
      }
    }

  @JSExportTopLevel("exportComputation")
  def exportComputation(c: Computation[Any]): js.Dynamic = js.Dynamic.literal(
    start = (host: js.Any) => stepToJs(c.start(hostFromJs(host))),
    effects = () => {
      val out = js.Array[String]()
      val i = c.effects().iterator()
      while (i.hasNext) out.push(nameOf(i.next()))
      out
    })

  // IMPORT: a JS computation to the kernel

  def stepFromJs[A](s: js.Dynamic): Step[A] = s.tag.asInstanceOf[String] match {
    case "done" => Step.Done[A](s.value.asInstanceOf[A])
    case "yield" =>
      val (op, back) = opFromJs(s.op)
      val k = s.k
      Step.Yield[Any, A](op, new Resume[Any, A] {
        def resume(x: Any): Step[A] = stepFromJs(k.resume(back(x).asInstanceOf[js.Any]))
        def fail(e: Throwable): Step[A] = stepFromJs(k.fail(jsError(e).asInstanceOf[js.Any]))
        def cancel(): Step[A] = stepFromJs(k.cancel())
      })
    case "failed" => Step.Failed[A](throwable(s.error))
    case _        => Step.Cancelled[A]()
  }

  def hostToJs(host: Answers): js.Dynamic = js.Dynamic.literal(
    answers = (effect: String) => host.answers(typeNamed(effect)),
    value = (o: js.Dynamic) => {
      val (op, back) = opFromJs(o)
      back(host.value(op)).asInstanceOf[js.Any]
    })

  @JSExportTopLevel("importComputation")
  def importComputation(jc: js.Dynamic): Computation[Any] = new Computation[Any] {
    def start(host: Answers): Step[Any] = stepFromJs(jc.start(hostToJs(host)))
    def effects(): java.util.Set[Type] = {
      val out = new java.util.HashSet[Type]()
      if (!js.isUndefined(jc.effects)) jc.effects().asInstanceOf[js.Array[String]].foreach(n => out.add(typeNamed(n)))
      out
    }
  }

  @JSExportTopLevel("typeName")
  def typeName(name: String): String = nameOf(typeNamed(name))
}

/** programs of the kernel's reference machine, for a JS implementation to run and to run under — its interop checks */
object Examples {
  private def prog[A](f: Effects => Prog[A]): Program[A] = new Program[A] { def in(e: Effects): Prog[A] = f(e) }
  private def fn[A, B](f: A => B): Fn[A, B] = new Fn[A, B] { def apply(a: A): B = f(a) }
  private val rd: Key[Reader[Integer]] = Reader.key[Integer](typeOf[Integer])
  private val st: Key[State[Integer]] = State.key[Integer](typeOf[Integer])
  private def set(ts: Type*): java.util.Set[Type] = { val s = new java.util.HashSet[Type](); ts.foreach(s.add); s }

  /** a reader's value n, a choice among n and n + 1, the choice times ten: `Reader[Integer]`, `Choose` */
  @JSExportTopLevel("exampleAskChoose")
  def askChoose(): js.Dynamic = KernelJs.exportComputation(Steps.computation(prog { e =>
    e.flatMap(Handlers.ask[Integer](rd).in(e), fn((n: Integer) =>
      e.map(Handlers.among[Integer](java.util.Arrays.asList(n, Integer.valueOf(n + 1))).in(e), fn((x: Integer) => Integer.valueOf(x * 10)))))
  }, set(rd.`type`(), Choose.TYPE)).asInstanceOf[Computation[Any]])

  /** n counted on a state: `State[Integer]` */
  @JSExportTopLevel("exampleCount")
  def count(n: Int): js.Dynamic = {
    def go(i: Int): Program[Integer] = prog { e =>
      if (i == 0) Handlers.get[Integer](st).in(e)
      else e.flatMap(Handlers.modify[Integer](st, fn((s: Integer) => Integer.valueOf(s + 1))).in(e), fn((_: Void) => go(i - 1).in(e)))
    }
    KernelJs.exportComputation(Steps.computation(go(n), set(st.`type`())).asInstanceOf[Computation[Any]])
  }

  /** a JS computation under the kernel's handlers: a reader of `n`, every path of its choices */
  @JSExportTopLevel("underReaderAll")
  def underReaderAll(jc: js.Dynamic, n: Int): js.Array[Any] = {
    val c = KernelJs.importComputation(jc)
    val r = Steps.run(Handlers.reader[Integer, java.util.List[Any]](rd, Integer.valueOf(n), Handlers.all(prog(e => e.foreign(c)))))
    val out = js.Array[Any]()
    r.forEach(x => out.push(x))
    out
  }

  /** a JS computation under the kernel's state from `s0`: [the last state, the value] */
  @JSExportTopLevel("underState")
  def underState(jc: js.Dynamic, s0: Int): js.Array[Any] = {
    val c = KernelJs.importComputation(jc)
    val r = Steps.run(Handlers.state[Integer, Any](st, Integer.valueOf(s0), prog(e => e.foreign(c))))
    js.Array(r.state(), r.value())
  }
}
