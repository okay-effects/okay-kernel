package okay.kernel.onone

import okay.kernel.steps.{Steps, StepsJava}
import okay.kernel.tck.TckSuite

/** the TCK on okay-one, nesting through the reference machines */
class TestTckOne extends TckSuite("okay-one", OnOne, List(Steps, StepsJava.INSTANCE))
