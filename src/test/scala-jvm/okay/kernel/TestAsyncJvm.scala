package okay.kernel

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import okay.{!, %, +, Eff}
import okay.std.*

/** ASYNC FOR REAL: callbacks called later, from other threads — the run going on in them; a state and a log inside */
abstract class TestAsyncJvm(m: Monad) extends munit.FunSuite:
  def runAsync[A](p: A ! Async): A =
    val latch = CountDownLatch(1)
    var r: Any = null
    var e: Throwable = null
    Async.start(p, m, a => { r = a; latch.countDown() }, x => { e = x; latch.countDown() })
    assert(latch.await(10, TimeUnit.SECONDS), "the run did not end")
    if e != null then throw e
    r.asInstanceOf[A]

  test("callbacks from a pool's threads, the run going on in each; a log and a state inside") {
    val pool = Executors.newFixedThreadPool(2)
    try
      def later(x: Int): Int ! Async = await[Int](cb => pool.execute(() => { Thread.sleep(1); cb.ok(x) }))
      val q: Int ! Writer % String + State % Int + Async =
        later(5).flatMap(x => put(x)).andThen(tell("five")).andThen(later(7)).flatMap(y => get[Int].map(_ + y))
      assertEquals(runAsync(writer(state(0, q))), (List("five"), (5, 12)))
    finally pool.shutdown()
  }

  test("a thousand awaits on virtual threads, each a blocking sleep") {
    def sleepy(i: Int): Int ! Async = await[Int](cb => Thread.ofVirtual().start(() => { Thread.sleep(5); cb.ok(i) }))
    def all(i: Int, acc: Int): Int ! Async = if i == 1000 then Eff.pure(acc) else sleepy(i).flatMap(x => all(i + 1, acc + x))
    assertEquals(runAsync(all(0, 0)), 999 * 1000 / 2)
  }

class TestAsyncJvmSteps extends TestAsyncJvm(Steps)
class TestAsyncJvmRef extends TestAsyncJvm(Ref)
