package okay.kernel.integration

import okay.kernel.Effects
import okay.kernel.steps.{Steps, StepsJava}
import okay.kernel.tck.TckSuite
import okay.java.kernel.OnJava
import okay.core.kernel.OnCore

/** EVERY IMPLEMENTATION, ONE PROCESS: the TCK on each bridge, nesting through all the others — a program of one run
 * under the handlers of another, through five machines: okay-core's two, okay-java's, the reference's two */
object All {
  val every: List[(String, Effects)] = List(
    "okay-core zero" -> OnCore.zero, "okay-core cont" -> OnCore.cont, "okay-java" -> OnJava.EFFECTS,
    "steps" -> Steps, "steps-java" -> StepsJava.INSTANCE)
  def through(name: String): List[Effects] = every.filterNot(_._1 == name).map(_._2)
}
class TestTckZero extends TckSuite("okay-core zero", OnCore.zero, All.through("okay-core zero"))
class TestTckCont extends TckSuite("okay-core cont", OnCore.cont, All.through("okay-core cont"))
class TestTckOnJava extends TckSuite("okay-java", OnJava.EFFECTS, All.through("okay-java"))
class TestTckSteps extends TckSuite("steps", Steps, All.through("steps"))
class TestTckStepsJava extends TckSuite("steps-java", StepsJava.INSTANCE, All.through("steps-java"))
