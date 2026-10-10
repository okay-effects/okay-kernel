package okay.kernel.integration

import okay.kernel.steps.{Steps, StepsJava}
import okay.kernel.tck.TckSuite
import okay.java.kernel.OnJava

/** EVERY IMPLEMENTATION, ONE PROCESS: the TCK on each bridge, nesting through the others — a program of one run under
 * the handlers of another, through three machines */
class TestTckOnJava extends TckSuite("okay-java", OnJava.EFFECTS, others = List(Steps, StepsJava.INSTANCE))
class TestTckStepsThroughOnJava extends TckSuite("steps through okay-java", Steps, others = List(OnJava.EFFECTS, StepsJava.INSTANCE))
