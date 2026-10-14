package okay.kernel.integration

import okay.kernel.Effects
import okay.kernel.steps.{Steps, StepsJava}
import okay.kernel.tck.TckSuite

/** EVERY IMPLEMENTATION, ONE PROCESS: the TCK on each, nesting through all the others — a program of one run under the
 * handlers of another, through four machines: okay-core's (the one monad) and okay-java's (each implementing the API
 * itself), the reference's two. okay (okay-kernel-okay) is checked by its own module's TCK: okay and okay-core both define
 * `okay.core.*` — one package, two libraries — so they cannot share a classpath */
object All {
  val every: List[(String, Effects)] = List(
    "okay-core" -> okay.core.kernel.OnCore,
    "okay-java" -> okay.core.Kernel.EFFECTS,
    "steps" -> Steps, "steps-java" -> StepsJava.INSTANCE)
  def through(name: String): List[Effects] = every.filterNot(_._1 == name).map(_._2)
}
class TestTckOnCore extends TckSuite("okay-core", okay.core.kernel.OnCore, All.through("okay-core"))
class TestTckOnJava extends TckSuite("okay-java", okay.core.Kernel.EFFECTS, All.through("okay-java"))
class TestTckSteps extends TckSuite("steps", Steps, All.through("steps"))
class TestTckStepsJava extends TckSuite("steps-java", StepsJava.INSTANCE, All.through("steps-java"))
