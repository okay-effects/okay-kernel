package okay.demo

import okay.{!, %, +, Eff}
import okay.std.*

/** A PROGRAM OF SCALA 3, the goal's: called from the tests, Java's and Scala 2's among them */
object Programs:
  /** a log of Strings and an await, its value */
  def p(a: Int): Int ! Writer % String + Async =
    for
      _ <- tell("got " + a)
      x <- await(a + 1)
      _ <- tell("awaited " + x)
    yield x * 2

  /** `p`'s handlers around it: what is left needs nothing */
  def handled(a: Int): (List[String], Int) ! Any = writer(async(p(a)))
