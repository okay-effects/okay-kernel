package okay.kernel.okaycore

import org.openjdk.jmh.annotations.{State as JmhState, *}
import java.util.concurrent.TimeUnit
import okay.{!, %, +, Eff}
import okay.kernel.{Monad, Steps}
import okay.std.*

/**
 * THE SAME PROGRAMS, EVERY MONAD: okay-java's mixed program (n orders: a base, the first of three suppliers — a
 * choice — a failure per order caught, the total a state, the failures a log) and an operation by a general clause,
 * n of them — written once on the kernel's typed view, run by the machine of steps, okay-core's zero and cont
 */
@JmhState(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class MonadsBenchmark:
  @Param(Array("steps", "zero", "cont"))
  var monad: String = ""
  @Param(Array("1000"))
  var n: Int = 0

  def m: Monad = monad match
    case "zero" => OnCore.zero
    case "cont" => OnCore.cont
    case _ => Steps

  val suppliers: Seq[Int] = Seq(0, 1, 2)

  def order(i: Int): Unit ! Writer % String + State % Int =
    val price: Int ! Throws % String = if i % 13 == 0 then raise("no stock") else
      first(among(suppliers).flatMap(s => if (i + s) % 3 == 0 then Eff.pure(100 + i % 7 + s) else fail[Int])).map(_.getOrElse(0))
    attempt(price).flatMap {
      case Right(p) => get[Int].flatMap(t => put(t + p))
      case Left(e) => tell(e)
    }

  def orders(i: Int): Unit ! Writer % String + State % Int =
    if i == n then Eff.pure(()) else order(i).flatMap(_ => Eff.delay(orders(i + 1)))

  @Benchmark
  def mixed(): Int =
    val (log, (total, _)) = Eff.run(writer(state(0, orders(0))), m)
    total + log.length

  def ticks(i: Int): Int ! Any = if i == 0 then Eff.pure(0) else
    attempt(raise[String]("x")).flatMap(_ => Eff.delay(ticks(i - 1)))

  /** a handler entered and an operation by its general clause, n times */
  @Benchmark
  def handlers(): Int = Eff.run(ticks(n), m)
