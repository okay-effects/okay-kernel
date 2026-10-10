package okay.kernel.bench

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.{State => JmhState, _}
import okay.kernel._
import okay.kernel.std.{Handlers, Reader, Results, State, Throws, Writer}
import okay.kernel.steps.{Steps, StepsJava}

/**
 * okay-java's MIXED PROGRAM, WRITTEN ONCE over the kernel's `Effects`: n orders, each priced from a reader's base,
 * its supplier the first of three that fits (a choice), a failure for some (a raise, a handler per order), the total a
 * state, the failures a log — run on every implementation: the reference machines, okay-core's two through its
 * bridge, okay-java's through its. Against okay-core's own MixedBenchmark (the same program in okay-core's words)
 */
@JmhState(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class MixedBenchmark {
  // okay-core-zero, okay-core-cont: out until okay-core's native implementation of the API 0.2 lands
  @Param(Array("steps", "steps-java", "okay-core-zero", "okay-core-cont", "okay-java"))
  var impl: String = ""
  @Param(Array("1000"))
  var n: Int = 0

  private var e: Effects = null

  @Setup def setup(): Unit = e = impl match {
    case "steps" => Steps
    case "steps-java" => StepsJava.INSTANCE
    case "okay-core-zero" => okay.core.kernel.OnCore.zero
    case "okay-core-cont" => okay.core.kernel.OnCore.cont
    case "okay-java" => okay.core.Kernel.EFFECTS
  }

  private def prog[A](f: Effects => Prog[A]): Program[A] = new Program[A] { def in(e: Effects): Prog[A] = f(e) }
  private def fn[A, B](f: A => B): Fn[A, B] = new Fn[A, B] { def apply(a: A): B = f(a) }
  private def int(i: Int): Integer = Integer.valueOf(i)

  private val rd = Reader.of(Type.of(classOf[Integer]))
  private val st = State.of(Type.of(classOf[Integer]))
  private val log = Writer.of(Type.of(classOf[String]))
  private val err = Throws.of(Type.of(classOf[String]))
  private val suppliers: java.util.List[Integer] = java.util.Arrays.asList(int(0), int(1), int(2))

  /** one order: its price, or its failure in the log; the total moved by its price */
  private def order(i: Int): Program[Void] = prog { x =>
    x.flatMap(Handlers.ask[Integer](rd).in(x), fn((base: Integer) => {
      val price: Program[Integer] =
        if (i % 13 == 0) Handlers.raise[String, Integer](err, "no stock")
        else prog(y => y.map(Handlers.first[Integer](prog(z => z.flatMap(Handlers.among[Integer](suppliers).in(z), fn((s: Integer) =>
          if ((i + s) % 3 == 0) z.pure(int(base + i % 7 + s)) else Handlers.among[Integer](java.util.Collections.emptyList[Integer]()).in(z))))).in(y),
          fn((o: java.util.Optional[Integer]) => o.orElse(int(0)))))
      x.flatMap(Handlers.throwsOf[String, Integer](err, price).in(x), fn((r: Results.Result[String, Integer]) => r match {
        case ok: Results.Ok[String @unchecked, Integer @unchecked] => Handlers.modify[Integer](st, fn((t: Integer) => int(t + ok.value()))).in(x)
        case e: Results.Err[String @unchecked, Integer @unchecked] => Handlers.tell[String](log, e.error()).in(x)
      }))
    }))
  }

  private def orders(i: Int): Program[Void] = prog { x =>
    if (i == n) x.pure(null: Void) else x.flatMap(order(i).in(x), fn((_: Void) => x.delay(new Thunk[Prog[Void]] { def get(): Prog[Void] = orders(i + 1).in(x) })))
  }

  @Benchmark
  def orders(): Int = {
    val p = Handlers.reader[Integer, Results.Logged[String, Results.Final[Integer, Void]]](rd, int(100),
      Handlers.writer[String, Results.Final[Integer, Void]](log, Handlers.state[Integer, Void](st, int(0), orders(0))))
    val r = e.computation(p, new java.util.HashSet[Type]()).start(Answers.NONE) match {
      case d: Step.Done[Results.Logged[String, Results.Final[Integer, Void]] @unchecked] => d.value()
      case other => throw new IllegalStateException(other.toString)
    }
    r.value().state().intValue + r.log().size
  }
}
